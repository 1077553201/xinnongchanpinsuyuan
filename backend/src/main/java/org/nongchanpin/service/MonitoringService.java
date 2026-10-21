package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.MonitoringMapper;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonitoringService {
    private final MonitoringMapper monitoring;
    private final ProductionMapper production;
    private final AuditService audit;

    @Data public static class Assign { @NotNull @Positive private Long organizationId; }
    @Data public static class Bind { @NotNull @Positive private Long deviceId; @NotNull @Positive private Long cycleId; }
    @Data public static class Close { @NotBlank @Size(max = 1000) private String reason; }

    public List<IotDeviceEntity> devices() { return monitoring.devices(AccessContext.organization()); }
    public List<MonitoringBindingEntity> bindings() { return monitoring.bindings(AccessContext.organization()); }
    public void checkCycle(Long id) {
        PlantingCycleEntity cycle = production.cycle(id);
        if (cycle == null) throw new IllegalArgumentException("种植周期不存在。");
        AccessContext.checkOrganization(production.plot(cycle.getPlotId()).getOrganizationId());
    }
    public List<IotSensorReadingEntity> readings(Long cycle) { checkCycle(cycle); return monitoring.readings(cycle); }
    public List<IotDataWindowEntity> windows(Long cycle) { checkCycle(cycle); return monitoring.windows(cycle); }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void assign(Long id, Assign request) {
        AccessContext.admin();
        IotDeviceEntity device = monitoring.lockDevice(id);
        if (device == null || "DELETED".equals(device.getStatus())) throw new IllegalArgumentException("设备不存在。");
        if (device.getOrganizationId() != null) throw new IllegalArgumentException("设备已有所属机构，本阶段不支持跨机构转移。");
        if (production.activeOrganization(request.getOrganizationId()) != 1) throw new IllegalArgumentException("机构不存在或已停用。");
        monitoring.assign(id, request.getOrganizationId(), AccessContext.require().getId());
        audit.record("DEVICE_ASSIGN", "DEVICE", id, "所属机构 " + request.getOrganizationId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public MonitoringBindingEntity bind(Bind request) {
        AccessContext.write();
        PlantingCycleEntity cycle = production.cycle(request.getCycleId());
        if (cycle == null) throw new IllegalArgumentException("种植周期不存在。");
        FarmPlotEntity plot = production.lockPlot(cycle.getPlotId());
        AccessContext.checkOrganization(plot.getOrganizationId());
        cycle = production.lockCycle(cycle.getId());
        if (!"ACTIVE".equals(cycle.getStatus())) throw new IllegalArgumentException("只能绑定正在种植的周期。");
        IotDeviceEntity device = monitoring.lockDevice(request.getDeviceId());
        if (device == null || "DELETED".equals(device.getStatus())) throw new IllegalArgumentException("设备不存在。");
        if (!plot.getOrganizationId().equals(device.getOrganizationId())) throw new IllegalArgumentException("请先由管理员确认设备所属机构，且设备与地块须属于同一机构。");
        String type = device.getDeviceType() == null ? "" : device.getDeviceType().toUpperCase();
        if (type.contains("CAR") || type.contains("ROBOT") || type.contains("VEHICLE") || type.contains("小车")) {
            throw new IllegalArgumentException("巡检小车须通过巡检任务确定地块，暂不支持按固定设备绑定。");
        }
        if (monitoring.activeBindings(device.getId()) != 0) throw new IllegalArgumentException("设备已有生效绑定，请先解除旧绑定。");
        MonitoringBindingEntity binding = new MonitoringBindingEntity();
        binding.setDeviceId(device.getId()); binding.setPlotId(plot.getId()); binding.setCycleId(cycle.getId());
        binding.setGatewayCode(device.getGatewayCode()); binding.setFarmId(device.getFarmId());
        binding.setStartedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)); binding.setCreatedBy(AccessContext.require().getId());
        monitoring.insertBinding(binding);
        audit.record("MONITORING_BIND", "BINDING", binding.getId(), "设备 " + device.getDeviceCode() + " / 周期 " + cycle.getCycleCode());
        return monitoring.binding(binding.getId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public MonitoringBindingEntity close(Long id, Close request) {
        AccessContext.write();
        MonitoringBindingEntity existing = monitoring.binding(id);
        if (existing == null) throw new IllegalArgumentException("绑定记录不存在。");
        FarmPlotEntity plot = production.lockPlot(existing.getPlotId());
        AccessContext.checkOrganization(plot.getOrganizationId());
        monitoring.lockDevice(existing.getDeviceId());
        if (existing.getEndedAt() != null) throw new IllegalArgumentException("绑定已经结束，请刷新核对。");
        existing.setEndedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        existing.setEndedBy(AccessContext.require().getId()); existing.setEndReason(request.getReason().trim());
        if (monitoring.close(existing) != 1) throw new IllegalArgumentException("绑定状态已变化，请刷新核对。");
        audit.record("MONITORING_UNBIND", "BINDING", id, existing.getEndReason());
        return monitoring.binding(id);
    }

    // MQTT 没有登录用户；来源归属由历史绑定决定，不能使用 HTTP 当前用户猜测归属。
    public void attribute(IotSensorReadingEntity reading, IotDeviceEntity device) {
        monitoring.lockDevice(device.getId());
        boolean missingTime = "MISSING_COLLECTION_TIME".equals(reading.getResolutionReason());
        reading.setBindingId(null); reading.setPlotId(null); reading.setCycleId(null);
        reading.setResolution("UNRESOLVED"); reading.setResolutionReason("NO_BINDING_AT_COLLECTION_TIME");
        if (missingTime) {
            reading.setResolutionReason("MISSING_COLLECTION_TIME");
        } else if (reading.getReportedAt().isAfter(LocalDateTime.now())) {
            reading.setResolutionReason("FUTURE_COLLECTION_TIME");
        } else {
            List<MonitoringBindingEntity> matches = monitoring.atTime(device.getId(), reading.getReportedAt());
            if (matches.size() == 1) {
                MonitoringBindingEntity binding = matches.get(0);
                if (Objects.equals(binding.getFarmId(), reading.getFarmId()) && Objects.equals(binding.getGatewayCode(), reading.getGatewayCode())) {
                    reading.setBindingId(binding.getId()); reading.setPlotId(binding.getPlotId()); reading.setCycleId(binding.getCycleId());
                    reading.setResolution("BOUND"); reading.setResolutionReason("MATCHED_HISTORICAL_BINDING");
                } else reading.setResolutionReason("GATEWAY_OR_FARM_MISMATCH");
            } else if (matches.size() > 1) reading.setResolutionReason("AMBIGUOUS_BINDING_HISTORY");
        }
        monitoring.attribute(reading);
    }
}
