package org.nongchanpin.service;

import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.entity.PlantingCycleEntity;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrowingAccess {
    private final ProductionMapper production;
    public PlantingCycleEntity cycle(Long id, boolean lock) {
        PlantingCycleEntity cycle = production.cycle(id);
        if (cycle == null) throw new IllegalArgumentException("种植周期不存在。");
        AccessContext.checkOrganization(production.plot(cycle.getPlotId()).getOrganizationId());
        if (lock) {
            AccessContext.write(); production.lockPlot(cycle.getPlotId()); cycle = production.lockCycle(id);
        }
        return cycle;
    }
    public void active(PlantingCycleEntity cycle) {
        if (!"ACTIVE".equals(cycle.getStatus())) throw new IllegalArgumentException("本轮种植已结束，不能新增生产安排或环境规则。");
    }
}
