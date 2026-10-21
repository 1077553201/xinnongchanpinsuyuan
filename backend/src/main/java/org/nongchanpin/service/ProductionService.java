package org.nongchanpin.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.model.dto.CreatePlotRequest;
import org.nongchanpin.model.dto.StartPlantingRequest;
import org.nongchanpin.model.dto.EndPlantingRequest;
import org.nongchanpin.model.entity.FarmPlotEntity;
import org.nongchanpin.model.entity.PlantingCycleEntity;
import org.nongchanpin.model.entity.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.nongchanpin.security.AccessContext;

@Service
@RequiredArgsConstructor
public class ProductionService {
    private final ProductionMapper production;
    private final ProductMapper products;
    private final AuditService audit;
    private final org.nongchanpin.mapper.GrowingMapper growing;
    private final org.nongchanpin.mapper.HarvestMapper harvests;

    public List<Map<String, Object>> organizations() { return production.organizations(AccessContext.organization()); }
    public List<FarmPlotEntity> plots() { return production.plots(AccessContext.organization()); }
    public List<PlantingCycleEntity> cycles() { return production.cycles(AccessContext.organization()); }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public FarmPlotEntity createPlot(CreatePlotRequest request) {
        AccessContext.write();
        AccessContext.checkOrganization(request.getOrganizationId());
        if (production.activeOrganization(request.getOrganizationId()) != 1) {
            throw new IllegalArgumentException("所属机构不存在或已停用。");
        }
        FarmPlotEntity plot = new FarmPlotEntity();
        plot.setOrganizationId(request.getOrganizationId());
        plot.setPlotCode(request.getPlotCode());
        plot.setPlotName(request.getPlotName().trim());
        plot.setLocation(request.getLocation().trim());
        plot.setAreaMu(request.getAreaMu());
        plot.setManagerName(request.getManagerName().trim());
        plot.setNotes(request.getNotes());
        production.insertPlot(plot);
        audit.record("PLOT_CREATE", "PLOT", plot.getId(), plot.getPlotCode());
        return plot;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PlantingCycleEntity start(Long plotId, StartPlantingRequest request) {
        AccessContext.write();
        FarmPlotEntity plot = production.lockPlot(plotId);
        if (plot == null) throw new IllegalArgumentException("地块不存在。");
        AccessContext.checkOrganization(plot.getOrganizationId());
        if (production.activeOrganization(plot.getOrganizationId()) != 1) {
            throw new IllegalArgumentException("所属机构已停用，不能开启种植。");
        }
        if (production.activeCycles(plotId) != 0) {
            throw new IllegalArgumentException("这块地已有进行中的种植，请先核对并结束本轮。");
        }
        ProductEntity product = products.findById(request.getProductId());
        if (product == null || !"ACTIVE".equals(product.getStatus())) {
            throw new IllegalArgumentException("请选择有效产品。");
        }
        if (!plot.getOrganizationId().equals(product.getOrganizationId())) {
            throw new IllegalArgumentException("产品与地块必须属于同一机构。");
        }
        if (request.getPlantedOn().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("实际种植日期不能晚于今天。");
        }
        if (request.getExpectedHarvestOn() != null
                && request.getExpectedHarvestOn().isBefore(request.getPlantedOn())) {
            throw new IllegalArgumentException("预计采收日期不能早于种植日期。");
        }
        PlantingCycleEntity cycle = new PlantingCycleEntity();
        cycle.setPlotId(plotId);
        cycle.setProductId(product.getId());
        cycle.setCycleCode(request.getCycleCode());
        // 保留当轮产品和品种快照，不随后续产品资料变更覆盖历史。
        cycle.setProductName(product.getProductName());
        cycle.setVariety(request.getVariety().trim());
        cycle.setPlantedOn(request.getPlantedOn());
        cycle.setExpectedHarvestOn(request.getExpectedHarvestOn());
        cycle.setManagerName(request.getManagerName().trim());
        cycle.setEnvironmentNotes(request.getEnvironmentNotes());
        cycle.setStatus("ACTIVE");
        if (production.laterHistory(cycle) != 0) {
            throw new IllegalArgumentException("种植日期与已有历史周期重叠，请核对日期。");
        }
        production.insertCycle(cycle);
        audit.record("PLANTING_START", "CYCLE", cycle.getId(), cycle.getCycleCode());
        return cycle;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PlantingCycleEntity end(Long id, EndPlantingRequest request) {
        AccessContext.write();
        PlantingCycleEntity existing = production.cycle(id);
        if (existing == null) throw new IllegalArgumentException("种植周期不存在。");
        AccessContext.checkOrganization(production.plot(existing.getPlotId()).getOrganizationId());
        // 开始与结束都锁定地块行，串行处理同一地块的状态变更。
        production.lockPlot(existing.getPlotId());
        PlantingCycleEntity cycle = production.lockCycle(id);
        if (!"ACTIVE".equals(cycle.getStatus())) {
            throw new IllegalArgumentException("本轮种植已结束，请刷新查看历史。");
        }
        if (production.openBindings(id) > 0) throw new IllegalArgumentException("本轮还有正在监测的设备，请先解除绑定并安排后续监测。");
        if (growing.pendingTasks(id) > 0) throw new IllegalArgumentException("本轮还有待执行农事，请先完成或取消任务。");
        if (growing.openCases(id) > 0) throw new IllegalArgumentException("本轮还有未记录处置的环境异常，请先核对处理。");
        if (harvests.openHarvests(id) > 0) throw new IllegalArgumentException("本轮还有未完成分级数量核对的采收批次，请先核对。");
        LocalDate lastHarvestDate = harvests.latestHarvestDate(id);
        if (lastHarvestDate != null && request.getEndedOn().isBefore(lastHarvestDate)) throw new IllegalArgumentException("结束日期不能早于本轮实际采收日期。");
        LocalDate lastExecutionDate = growing.latestExecutionDate(id);
        if (lastExecutionDate != null && request.getEndedOn().isBefore(lastExecutionDate)) throw new IllegalArgumentException("结束日期不能早于本轮实际农事执行日期。");
        LocalDate lastMonitoringDate = production.latestMonitoringDate(id);
        if (lastMonitoringDate != null && request.getEndedOn().isBefore(lastMonitoringDate)) {
            throw new IllegalArgumentException("结束日期不能早于本轮设备监测结束日期。");
        }
        if (request.getEndedOn().isBefore(cycle.getPlantedOn()) || request.getEndedOn().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("结束日期须在种植日期与今天之间。");
        }
        cycle.setEndedOn(request.getEndedOn());
        cycle.setEndReason(request.getReason().trim());
        if (production.endCycle(cycle) != 1) throw new IllegalArgumentException("状态已变化，请刷新后核对。");
        growing.retireCycleRules(id, java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        audit.record("PLANTING_END", "CYCLE", id, cycle.getEndReason());
        return production.cycle(id);
    }
}
