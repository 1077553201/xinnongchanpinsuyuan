package org.nongchanpin.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.GrowingMapper;
import org.nongchanpin.model.dto.GrowingRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@org.springframework.validation.annotation.Validated
@RequiredArgsConstructor
public class GrowingEnvironmentService {
    private final GrowingMapper growing;
    private final GrowingAccess access;
    private final AuditService audit;
    private final PayloadService payloads;

    public Map<String, Object> overview(Long cycleId) {
        PlantingCycleEntity cycle = access.cycle(cycleId, false);
        List<GrowingRuleEntity> rules = growing.rules(cycleId);
        List<Map<String, Object>> coverage = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (GrowingRuleEntity rule : rules) {
            if (rule.getEffectiveTo() != null) continue;
            LocalDateTime latest = growing.latestRuleReading(rule.getId());
            String status;
            if (!"ACTIVE".equals(cycle.getStatus())) status = "NOT_ACTIVE";
            else if (latest == null) status = now.isAfter(rule.getEffectiveFrom().plusMinutes(rule.getExpectedIntervalMinutes())) ? "NO_DATA" : "WAITING";
            else status = now.isAfter(latest.plusMinutes(rule.getExpectedIntervalMinutes())) ? "STALE" : "CURRENT";
            Map<String, Object> row = new LinkedHashMap<>(); row.put("ruleId", rule.getId()); row.put("metric", rule.getMetric());
            row.put("latestReportedAt", latest); row.put("status", status); coverage.add(row);
        }
        return Map.of("rules", rules, "coverage", coverage, "assessments", growing.assessments(cycleId), "openCases", growing.openCases(cycleId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GrowingRuleEntity configure(Long cycleId, @javax.validation.Valid GrowingRequests.Rule request) {
        PlantingCycleEntity cycle = access.cycle(cycleId, true);
        validateRange(request);
        Map<String, Object> content = payloads.map();
        content.put("cycle", cycleId); content.put("actor", AccessContext.require().getId()); content.put("metric", request.getMetric());
        content.put("min", number(request.getMinValue())); content.put("max", number(request.getMaxValue()));
        content.put("interval", request.getExpectedIntervalMinutes()); content.put("basis", request.getBasis().trim()); content.put("notes", text(request.getNotes()));
        String hash = payloads.requestHash(content);
        GrowingRuleEntity existing = growing.ruleByRequest(request.getRequestKey());
        if (existing != null) {
            if (!Objects.equals(existing.getRequestHash(), hash)) throw new IllegalArgumentException("操作标识已用于其他内容，请刷新核对。");
            return existing;
        }
        access.active(cycle);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        growing.retireMetric(cycleId, request.getMetric(), now);
        GrowingRuleEntity rule = new GrowingRuleEntity(); rule.setCycleId(cycleId); rule.setMetric(request.getMetric());
        rule.setMinValue(request.getMinValue()); rule.setMaxValue(request.getMaxValue()); rule.setUnit(unit(request.getMetric()));
        rule.setExpectedIntervalMinutes(request.getExpectedIntervalMinutes()); rule.setBasis(request.getBasis().trim()); rule.setNotes(text(request.getNotes()));
        rule.setEffectiveFrom(now); rule.setCreatedBy(AccessContext.require().getId()); rule.setRequestKey(request.getRequestKey()); rule.setRequestHash(hash);
        growing.insertRule(rule); audit.record("ENVIRONMENT_RULE_SET", "RULE", rule.getId(), request.getMetric() + " / 周期 " + cycleId);
        return growing.rule(rule.getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void retire(Long id, @javax.validation.Valid GrowingRequests.Reason request) {
        GrowingRuleEntity rule = growing.rule(id);
        if (rule == null) throw new IllegalArgumentException("环境规则不存在。");
        access.active(access.cycle(rule.getCycleId(), true));
        if (growing.retireRule(id, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)) != 1) throw new IllegalArgumentException("该版本已结束，请刷新核对。");
        audit.record("ENVIRONMENT_RULE_RETIRE", "RULE", id, request.getReason().trim());
    }

    public List<EnvironmentAssessmentEntity> cases(Long cycleId) { access.cycle(cycleId, false); return growing.cases(cycleId); }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnvironmentAssessmentEntity handle(Long id, @javax.validation.Valid GrowingRequests.HandleCase request) {
        EnvironmentAssessmentEntity found = growing.caseById(id);
        if (found == null) throw new IllegalArgumentException("异常记录不存在。");
        access.cycle(found.getCycleId(), true);
        if (growing.handleCase(id, AccessContext.require().getId(), LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), request.getActionTaken().trim(), text(request.getObservation())) != 1) {
            throw new IllegalArgumentException("该异常已记录处置，请刷新核对。");
        }
        audit.record("ENVIRONMENT_CASE_HANDLE", "ENV_CASE", id, request.getActionTaken().trim());
        return growing.caseById(id);
    }

    // MQTT 路径：不使用 HTTP 用户身份；按采集时刻选择当时规则，不回填旧样本。
    public void assess(IotSensorReadingEntity reading) {
        if (!"RAW".equals(reading.getStatus()) || !"BOUND".equals(reading.getResolution()) || reading.getCycleId() == null) return;
        EnvironmentAssessmentEntity result = new EnvironmentAssessmentEntity(); result.setReadingId(reading.getId()); result.setCycleId(reading.getCycleId()); result.setMetric(reading.getMetric());
        List<GrowingRuleEntity> rules = growing.rulesAt(reading.getCycleId(), reading.getMetric(), reading.getReportedAt());
        if (rules.size() != 1) {
            result.setResult("NO_RULE"); result.setReason(rules.isEmpty() ? "采集时尚无适用规则，不能判断是否符合要求。" : "规则历史存在歧义，待核实。");
        } else {
            GrowingRuleEntity rule = rules.get(0); result.setRuleId(rule.getId());
            if (!Objects.equals(rule.getUnit(), canonicalUnit(reading.getUnit()))) {
                result.setResult("UNIT_MISMATCH"); result.setReason("报文单位与规则不符，未进行数值换算或合格判断。");
            } else if (rule.getMinValue() != null && reading.getValue().compareTo(rule.getMinValue()) < 0) {
                result.setResult("BELOW"); result.setReason("低于采集时适用规则的下限。");
            } else if (rule.getMaxValue() != null && reading.getValue().compareTo(rule.getMaxValue()) > 0) {
                result.setResult("ABOVE"); result.setReason("高于采集时适用规则的上限。");
            } else { result.setResult("IN_RANGE"); result.setReason("该条样本处于当时规则范围内，不代表全周期或产品质量合格。"); }
        }
        growing.insertAssessment(result);
        if ("BELOW".equals(result.getResult()) || "ABOVE".equals(result.getResult())) growing.insertCase(result);
    }

    private void validateRange(GrowingRequests.Rule request) {
        BigDecimal min = request.getMinValue(), max = request.getMaxValue();
        unit(request.getMetric());
        if (min == null && max == null) throw new IllegalArgumentException("至少填写一个环境范围边界。");
        if (min != null && max != null && min.compareTo(max) > 0) throw new IllegalArgumentException("下限不能大于上限。");
        if (List.of("humidity", "soil_moisture").contains(request.getMetric())) {
            if ((min != null && (min.signum() < 0 || min.compareTo(new BigDecimal("100")) > 0))
                    || (max != null && (max.signum() < 0 || max.compareTo(new BigDecimal("100")) > 0))) throw new IllegalArgumentException("百分比范围须在 0–100 之间。");
        }
        if ("light".equals(request.getMetric()) && ((min != null && min.signum() < 0) || (max != null && max.signum() < 0))) throw new IllegalArgumentException("光照范围不能为负数。");
    }
    private String unit(String metric) {
        switch (metric) { case "temperature": return "celsius"; case "humidity": case "soil_moisture": return "%"; case "light": return "lux"; default: throw new IllegalArgumentException("暂不支持该环境指标。"); }
    }
    private String canonicalUnit(String value) {
        if (value == null) return "";
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "celsius": case "°c": case "℃": case "c": return "celsius";
            case "%": case "percent": case "pct": return "%";
            case "lux": case "lx": return "lux";
            default: return value.trim();
        }
    }
    private String number(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    private String text(String value) { return value == null ? "" : value.trim(); }
}
