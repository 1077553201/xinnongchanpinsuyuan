package org.nongchanpin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.HarvestMapper;
import org.nongchanpin.model.dto.HarvestRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class HarvestService {
    private final HarvestMapper harvests;
    private final GrowingAccess access;
    private final AuditService audit;
    private final PayloadService payloads;

    public List<HarvestLotEntity> list(Long cycle) { access.cycle(cycle, false); return harvests.harvests(cycle); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id) {
        HarvestLotEntity lot = requireHarvest(id); access.cycle(lot.getCycleId(), false);
        return Map.of("harvest", lot, "grades", harvests.grades(id), "packings", harvests.packings(id));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public HarvestLotEntity create(Long cycleId, @Valid HarvestRequests.Harvest request) {
        PlantingCycleEntity cycle = access.cycle(cycleId, true);
        HarvestLotEntity record = new HarvestLotEntity(); record.setCycleId(cycleId); record.setLotCode(request.getLotCode());
        record.setHarvestedAt(request.getHarvestedAt()); record.setQuantity(request.getQuantity().stripTrailingZeros()); record.setUnit(request.getUnit());
        record.setWeightKg(toKg(request.getQuantity(), request.getUnit())); record.setPerformerName(request.getPerformerName().trim()); record.setNotes(text(request.getNotes()));
        record.setRecordedBy(AccessContext.require().getId()); record.setRecorderName(AccessContext.require().getDisplayName());
        String hash = payloads.requestHash(record);
        HarvestLotEntity existing = harvests.harvestByRequest(request.getRequestKey());
        if (existing != null) { same(hash, existing.getRequestHash()); return harvests.harvest(existing.getId()); }
        access.active(cycle);
        time(request.getHarvestedAt(), cycle.getPlantedOn().atStartOfDay());
        record.setRequestKey(request.getRequestKey()); record.setRequestHash(hash);
        harvests.insertHarvest(record); audit.record("HARVEST_RECORD", "HARVEST", record.getId(), record.getLotCode());
        return harvests.harvest(record.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public HarvestGradeEntity grade(Long id, @Valid HarvestRequests.Grade request) {
        HarvestLotEntity lot = lockedHarvest(id);
        HarvestGradeEntity record = new HarvestGradeEntity(); record.setHarvestId(id); record.setKind(request.getKind()); record.setGradeName(request.getGradeName().trim());
        record.setWeightKg(weight(request.getWeightKg())); record.setHandledAt(request.getHandledAt()); record.setPerformerName(request.getPerformerName().trim());
        record.setLocation(text(request.getLocation())); record.setReason(text(request.getReason()));
        record.setRecordedBy(AccessContext.require().getId()); record.setRecorderName(AccessContext.require().getDisplayName());
        String hash = payloads.requestHash(record);
        HarvestGradeEntity existing = harvests.gradeByRequest(request.getRequestKey());
        if (existing != null) { same(hash, existing.getRequestHash()); return harvests.grade(existing.getId()); }
        if (!"OPEN".equals(lot.getStatus())) throw new IllegalArgumentException("本批已完成分级核对，不能追加分级或损耗。");
        time(request.getHandledAt(), lot.getHarvestedAt());
        if ("LOSS".equals(request.getKind()) && record.getReason().isEmpty()) throw new IllegalArgumentException("登记损耗必须填写实际原因。");
        if ("SALEABLE".equals(request.getKind()) && record.getLocation().isEmpty()) throw new IllegalArgumentException("请填写分级后实际存放位置。");
        if (harvests.gradeCount(id) >= 200) throw new IllegalArgumentException("本批已达 200 条分级记录上限，请联系管理员核对。");
        if (harvests.allocated(id).add(record.getWeightKg()).compareTo(lot.getWeightKg()) > 0) throw new IllegalArgumentException("分级与损耗合计不能超过本批实际采收净重。");
        record.setRequestKey(request.getRequestKey()); record.setRequestHash(hash);
        harvests.insertGrade(record); audit.record("HARVEST_GRADE", "HARVEST", id, record.getKind() + " / 记录 " + record.getId());
        return harvests.grade(record.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public HarvestLotEntity finish(Long id) {
        HarvestLotEntity lot = lockedHarvest(id);
        if ("GRADED".equals(lot.getStatus())) return harvests.harvest(id);
        if (harvests.allocated(id).compareTo(lot.getWeightKg()) != 0) throw new IllegalArgumentException("分级重量加损耗尚未等于采收净重，请先核对差额。");
        if (harvests.finish(id) != 1) throw new IllegalArgumentException("采收状态已变化，请刷新核对。");
        audit.record("HARVEST_RECONCILE", "HARVEST", id, "分级与损耗总量已核对");
        return harvests.harvest(id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PackingLotEntity pack(Long gradeId, @Valid HarvestRequests.Packing request) {
        HarvestGradeEntity initial = harvests.grade(gradeId);
        if (initial == null) throw new IllegalArgumentException("分级来源不存在。");
        HarvestLotEntity lot = lockedHarvest(initial.getHarvestId());
        PackingLotEntity record = new PackingLotEntity(); record.setGradeId(gradeId); record.setPackingCode(request.getPackingCode()); record.setPackedAt(request.getPackedAt());
        record.setPackageCount(request.getPackageCount()); record.setPackageUnit(request.getPackageUnit().trim()); record.setNetWeightKg(weight(request.getNetWeightKg()));
        record.setTotalWeightKg(weight(record.getNetWeightKg().multiply(BigDecimal.valueOf(request.getPackageCount()))));
        record.setPerformerName(request.getPerformerName().trim()); record.setLocation(request.getLocation().trim()); record.setNotes(text(request.getNotes()));
        record.setRecordedBy(AccessContext.require().getId()); record.setRecorderName(AccessContext.require().getDisplayName());
        String hash = payloads.requestHash(record);
        PackingLotEntity existing = harvests.packingByRequest(request.getRequestKey());
        if (existing != null) { same(hash, existing.getRequestHash()); return existing; }
        if (!"GRADED".equals(lot.getStatus())) throw new IllegalArgumentException("请先完成本批分级总量核对，再登记包装。");
        if (!"SALEABLE".equals(initial.getKind())) throw new IllegalArgumentException("损耗记录不能作为包装来源。");
        time(request.getPackedAt(), initial.getHandledAt());
        if (harvests.packed(gradeId).add(record.getTotalWeightKg()).compareTo(initial.getWeightKg()) > 0) throw new IllegalArgumentException("包装净重超过该等级的未包装余量。");
        record.setRequestKey(request.getRequestKey()); record.setRequestHash(hash);
        harvests.insertPacking(record); audit.record("PACKING_RECORD", "PACKING", record.getId(), record.getPackingCode());
        return harvests.packing(record.getId());
    }
    private HarvestLotEntity requireHarvest(Long id) {
        HarvestLotEntity lot = harvests.harvest(id); if (lot == null) throw new IllegalArgumentException("采收批次不存在。"); return lot;
    }
    private HarvestLotEntity lockedHarvest(Long id) {
        HarvestLotEntity lot = requireHarvest(id); access.cycle(lot.getCycleId(), true); return harvests.lockHarvest(id);
    }
    public static BigDecimal toKg(BigDecimal value, String unit) {
        BigDecimal factor;
        switch (unit) { case "kg": factor = BigDecimal.ONE; break; case "g": factor = new BigDecimal("0.001"); break; case "jin": factor = new BigDecimal("0.5"); break; default: throw new IllegalArgumentException("仅支持公斤、克或市斤（500 克）。"); }
        return weight(value.multiply(factor));
    }
    private static BigDecimal weight(BigDecimal value) {
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("999999999.999")) > 0) throw new IllegalArgumentException("重量须大于 0 且不超过 999999999.999 公斤。");
        try { return value.setScale(3, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("重量精度为 1 克，请核对数量，系统不会自动舍入。"); }
    }
    private void time(LocalDateTime value, LocalDateTime earliest) {
        if (value.isBefore(earliest) || value.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("实际时间不能早于来源记录，且不能晚于当前时间。");
    }
    private void same(String actual, String expected) { if (!actual.equals(expected)) throw new IllegalArgumentException("操作标识已用于其他内容，请刷新核对。"); }
    private String text(String value) { return value == null ? "" : value.trim(); }
}
