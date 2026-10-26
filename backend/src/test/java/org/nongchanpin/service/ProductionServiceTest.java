package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.nongchanpin.security.AccessContext;
import org.nongchanpin.security.Actor;
import org.junit.jupiter.api.Test;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.model.dto.EndPlantingRequest;
import org.nongchanpin.model.dto.StartPlantingRequest;
import org.nongchanpin.model.entity.FarmPlotEntity;
import org.nongchanpin.model.entity.PlantingCycleEntity;
import org.nongchanpin.model.entity.ProductEntity;

class ProductionServiceTest {
    private final ProductionMapper mapper = mock(ProductionMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final org.nongchanpin.mapper.GrowingMapper growing = mock(org.nongchanpin.mapper.GrowingMapper.class);
    private final org.nongchanpin.mapper.HarvestMapper harvests = mock(org.nongchanpin.mapper.HarvestMapper.class);
    private final ProductionService service = new ProductionService(mapper, products, mock(AuditService.class), growing, harvests);
    private final StartPlantingRequest start = new StartPlantingRequest();
    private final PlantingCycleEntity cycle = new PlantingCycleEntity();
    private final ProductEntity product = new ProductEntity();

    @BeforeEach
    void setup() {
        Actor actor = new Actor(); actor.setId(1L); actor.setRole("ADMIN"); actor.setOrganizationId(3L); AccessContext.set(actor);
        FarmPlotEntity plot = new FarmPlotEntity();
        plot.setId(1L);
        plot.setOrganizationId(3L);
        when(mapper.lockPlot(1L)).thenReturn(plot);
        when(mapper.plot(1L)).thenReturn(plot);
        when(mapper.activeOrganization(3L)).thenReturn(1);
        product.setId(2L);
        product.setOrganizationId(3L);
        product.setProductName("草莓");
        product.setStatus("ACTIVE");
        when(products.findById(2L)).thenReturn(product);
        start.setProductId(2L);
        start.setCycleCode("EAST-2026-A");
        start.setVariety("红颜");
        start.setPlantedOn(LocalDate.now().minusDays(10));
        start.setManagerName("张师傅");
        cycle.setId(7L);
        cycle.setPlotId(1L);
        cycle.setPlantedOn(start.getPlantedOn());
        cycle.setStatus("ACTIVE");
        when(mapper.cycle(7L)).thenReturn(cycle);
        when(mapper.lockCycle(7L)).thenReturn(cycle);
    }

    @AfterEach void clearIdentity() { AccessContext.clear(); }

    @Test void cannotEndWithUnreconciledHarvest() {
        when(harvests.openHarvests(7L)).thenReturn(1);
        EndPlantingRequest request = new EndPlantingRequest(); request.setEndedOn(LocalDate.now()); request.setReason("收尾");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L,request)); verify(mapper,never()).endCycle(any());
    }
    @Test void cannotEndBeforeActualHarvestDate() {
        when(harvests.latestHarvestDate(7L)).thenReturn(LocalDate.now());
        EndPlantingRequest request = new EndPlantingRequest(); request.setEndedOn(LocalDate.now().minusDays(1)); request.setReason("收尾");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L,request)); verify(mapper,never()).endCycle(any());
    }

    @Test
    void preservesProductAndVarietySnapshotWhenStarting() {
        PlantingCycleEntity created = service.start(1L, start);
        product.setProductName("新名称");
        assertEquals("草莓", created.getProductName());
        assertEquals("红颜", created.getVariety());
        verify(mapper).lockPlot(1L);
        verify(mapper).insertCycle(created);
    }

    @Test
    void rejectsSecondActiveCycleBeforeWriting() {
        when(mapper.activeCycles(1L)).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.start(1L, start));
        verify(mapper, never()).insertCycle(any());
    }

    @Test
    void rejectsProductFromAnotherOrganization() {
        product.setOrganizationId(9L);
        assertThrows(IllegalArgumentException.class, () -> service.start(1L, start));
        verify(mapper, never()).insertCycle(any());
    }

    @Test
    void rejectsInactiveProductAndInvalidDates() {
        product.setStatus("DELETED");
        assertThrows(IllegalArgumentException.class, () -> service.start(1L, start));
        product.setStatus("ACTIVE");
        start.setExpectedHarvestOn(start.getPlantedOn().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.start(1L, start));
        start.setExpectedHarvestOn(null);
        start.setPlantedOn(LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.start(1L, start));
        verify(mapper, never()).insertCycle(any());
    }

    @Test
    void rejectsBackdatingIntoPreviousCycle() {
        when(mapper.laterHistory(any())).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.start(1L, start));
        verify(mapper, never()).insertCycle(any());
    }

    @Test
    void rejectsInvalidEndDateAndRepeatedEnd() {
        EndPlantingRequest request = new EndPlantingRequest();
        request.setReason("采收结束");
        request.setEndedOn(start.getPlantedOn().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request));
        request.setEndedOn(LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request));
        request.setEndedOn(LocalDate.now());
        cycle.setStatus("ENDED");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request));
        verify(mapper, never()).endCycle(any());
    }

    @Test
    void endsCycleWithoutDeletingPlotOrHistory() {
        EndPlantingRequest request = new EndPlantingRequest();
        request.setEndedOn(LocalDate.now());
        request.setReason(" 本轮采收结束 ");
        when(mapper.endCycle(cycle)).thenReturn(1);
        service.end(7L, request);
        verify(mapper).lockPlot(1L);
        assertEquals("本轮采收结束", cycle.getEndReason());
        assertEquals(LocalDate.now(), cycle.getEndedOn());
        verify(mapper).endCycle(cycle);
        verify(growing).retireCycleRules(eq(7L), any());
    }

    @Test void cannotEndWhileMonitoringDevicesRemainBound() {
        when(mapper.openBindings(7L)).thenReturn(1);
        EndPlantingRequest request = new EndPlantingRequest(); request.setEndedOn(LocalDate.now()); request.setReason("结束");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request));
        verify(mapper, never()).endCycle(any());
    }
    @Test void cannotBackdateEndBeforeLastMonitoringActivity() {
        when(mapper.latestMonitoringDate(7L)).thenReturn(LocalDate.now());
        EndPlantingRequest request = new EndPlantingRequest(); request.setEndedOn(LocalDate.now().minusDays(1)); request.setReason("结束");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request)); verify(mapper, never()).endCycle(any());
    }
    @Test void pendingFarmTasksPreventEnding() {
        when(growing.pendingTasks(7L)).thenReturn(1);
        EndPlantingRequest request = new EndPlantingRequest(); request.setEndedOn(LocalDate.now()); request.setReason("结束");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request)); verify(mapper, never()).endCycle(any());
    }
    @Test void unhandledEnvironmentalCasesPreventEnding() {
        when(growing.openCases(7L)).thenReturn(1);
        EndPlantingRequest request = new EndPlantingRequest(); request.setEndedOn(LocalDate.now()); request.setReason("结束");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request)); verify(mapper, never()).endCycle(any());
    }
    @Test void endCannotPrecedeActualFarmExecutionDate() {
        when(growing.latestExecutionDate(7L)).thenReturn(LocalDate.now());
        EndPlantingRequest request = new EndPlantingRequest(); request.setEndedOn(LocalDate.now().minusDays(1)); request.setReason("结束");
        assertThrows(IllegalArgumentException.class, () -> service.end(7L, request)); verify(mapper, never()).endCycle(any());
    }
    @Test void historicalOrganizationNamesRemainScopedToCurrentInstitution() {
        AccessContext.require().setRole("MANAGER");
        service.organizationNames(); verify(mapper).organizationNames(3L);
    }
    @Test void administratorCanResolveHistoricalNamesAcrossOrganizations() {
        AccessContext.require().setRole("ADMIN"); service.organizationNames(); verify(mapper).organizationNames(null);
    }
}
