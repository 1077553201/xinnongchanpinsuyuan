package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.HarvestMapper;
import org.nongchanpin.model.dto.HarvestRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;

class HarvestServiceTest {
    private final HarvestMapper mapper = mock(HarvestMapper.class);
    private final GrowingAccess access = mock(GrowingAccess.class);
    private final HarvestService service = new HarvestService(mapper, access, mock(AuditService.class), new PayloadService());
    private final HarvestLotEntity lot = new HarvestLotEntity();
    private final HarvestGradeEntity grade = new HarvestGradeEntity();
    private final PlantingCycleEntity cycle = new PlantingCycleEntity();
    @BeforeEach void setup() {
        Actor actor = new Actor(); actor.setId(1L); actor.setDisplayName("录入人"); actor.setRole("MANAGER"); AccessContext.set(actor);
        cycle.setId(7L); cycle.setPlantedOn(java.time.LocalDate.now().minusDays(5)); cycle.setStatus("ACTIVE");
        when(access.cycle(7L,true)).thenReturn(cycle);
        lot.setId(2L); lot.setCycleId(7L); lot.setStatus("OPEN"); lot.setWeightKg(new BigDecimal("40.000")); lot.setHarvestedAt(LocalDateTime.now().minusDays(2));
        when(mapper.harvest(2L)).thenReturn(lot); when(mapper.lockHarvest(2L)).thenReturn(lot); when(mapper.allocated(2L)).thenReturn(BigDecimal.ZERO);
        grade.setId(3L); grade.setHarvestId(2L); grade.setKind("SALEABLE"); grade.setWeightKg(new BigDecimal("35.000")); grade.setHandledAt(LocalDateTime.now().minusDays(1));
        when(mapper.grade(3L)).thenReturn(grade); when(mapper.packed(3L)).thenReturn(BigDecimal.ZERO);
    }
    @AfterEach void clear() { AccessContext.clear(); }
    private HarvestRequests.Harvest harvest() {
        HarvestRequests.Harvest r = new HarvestRequests.Harvest(); r.setLotCode("HARVEST-TEST"); r.setHarvestedAt(LocalDateTime.now().minusDays(2));
        r.setQuantity(new BigDecimal("80")); r.setUnit("jin"); r.setPerformerName("现场采收人"); r.setRequestKey("00000000-0000-4000-8000-000000000001"); return r;
    }
    private HarvestRequests.Grade allocation() {
        HarvestRequests.Grade r = new HarvestRequests.Grade(); r.setKind("SALEABLE"); r.setGradeName("一级"); r.setWeightKg(new BigDecimal("35"));
        r.setHandledAt(LocalDateTime.now().minusDays(1)); r.setPerformerName("分级人"); r.setLocation("冷库一号"); r.setRequestKey("00000000-0000-4000-8000-000000000002"); return r;
    }
    private HarvestRequests.Packing packing() {
        HarvestRequests.Packing r = new HarvestRequests.Packing(); r.setPackingCode("PACK-TEST"); r.setPackedAt(LocalDateTime.now().minusHours(1));
        r.setPackageCount(7); r.setPackageUnit("箱"); r.setNetWeightKg(new BigDecimal("5")); r.setPerformerName("包装人"); r.setLocation("冷库二号"); r.setRequestKey("00000000-0000-4000-8000-000000000003"); return r;
    }
    @Test void convertsExplicitMassUnitsWithoutRounding() {
        assertEquals(new BigDecimal("40.000"), HarvestService.toKg(new BigDecimal("80"),"jin"));
        assertEquals(new BigDecimal("1.000"), HarvestService.toKg(new BigDecimal("1000"),"g"));
        assertThrows(IllegalArgumentException.class, () -> HarvestService.toKg(new BigDecimal("1.1"),"g"));
        assertThrows(IllegalArgumentException.class, () -> HarvestService.toKg(BigDecimal.ONE,"箱"));
    }
    @Test void recordsCanonicalWeightAndSeparateActorThenDeduplicates() {
        HarvestRequests.Harvest request = harvest();
        doAnswer(call -> { HarvestLotEntity e=call.getArgument(0); e.setId(2L); assertEquals(new BigDecimal("40.000"),e.getWeightKg()); assertEquals("现场采收人",e.getPerformerName()); assertEquals("录入人",e.getRecorderName()); when(mapper.harvestByRequest(request.getRequestKey())).thenReturn(e); return 1; }).when(mapper).insertHarvest(any());
        service.create(7L,request); service.create(7L,request); verify(mapper,times(1)).insertHarvest(any());
        request.setQuantity(new BigDecimal("81")); assertThrows(IllegalArgumentException.class, () -> service.create(7L,request));
    }
    @Test void rejectsFutureHarvest() { HarvestRequests.Harvest r=harvest(); r.setHarvestedAt(LocalDateTime.now().plusDays(1)); assertThrows(IllegalArgumentException.class, () -> service.create(7L,r)); verify(mapper,never()).insertHarvest(any()); }
    @Test void rejectsHarvestBeforePlanting() { HarvestRequests.Harvest r=harvest(); r.setHarvestedAt(cycle.getPlantedOn().minusDays(1).atStartOfDay()); assertThrows(IllegalArgumentException.class, () -> service.create(7L,r)); }
    @Test void rejectsAllocationAboveTotal() { when(mapper.allocated(2L)).thenReturn(new BigDecimal("10")); assertThrows(IllegalArgumentException.class, () -> service.grade(2L,allocation())); verify(mapper,never()).insertGrade(any()); }
    @Test void lossNeedsReason() { HarvestRequests.Grade r=allocation(); r.setKind("LOSS"); assertThrows(IllegalArgumentException.class, () -> service.grade(2L,r)); }
    @Test void saleableGradeNeedsStorageLocation() { HarvestRequests.Grade r=allocation(); r.setLocation(" "); assertThrows(IllegalArgumentException.class, () -> service.grade(2L,r)); }
    @Test void preventsNewAllocationAfterReconciliation() { lot.setStatus("GRADED"); assertThrows(IllegalArgumentException.class, () -> service.grade(2L,allocation())); }
    @Test void requiresExactReconciliation() { when(mapper.allocated(2L)).thenReturn(new BigDecimal("39.999")); assertThrows(IllegalArgumentException.class, () -> service.finish(2L)); verify(mapper,never()).finish(any()); }
    @Test void reconcilesOnceAndAllowsReplay() { when(mapper.allocated(2L)).thenReturn(new BigDecimal("40")); when(mapper.finish(2L)).thenAnswer(call->{lot.setStatus("GRADED");return 1;}); service.finish(2L); service.finish(2L); verify(mapper,times(1)).finish(2L); }
    @Test void packagingRequiresReconciledHarvest() { assertThrows(IllegalArgumentException.class, () -> service.pack(3L,packing())); }
    @Test void lossCannotBePacked() { lot.setStatus("GRADED"); grade.setKind("LOSS"); assertThrows(IllegalArgumentException.class, () -> service.pack(3L,packing())); }
    @Test void packagingCannotOverdrawSource() { lot.setStatus("GRADED"); when(mapper.packed(3L)).thenReturn(new BigDecimal("0.001")); assertThrows(IllegalArgumentException.class, () -> service.pack(3L,packing())); verify(mapper,never()).insertPacking(any()); }
    @Test void packagingCannotPrecedeGrade() { lot.setStatus("GRADED"); HarvestRequests.Packing r=packing(); r.setPackedAt(lot.getHarvestedAt()); assertThrows(IllegalArgumentException.class, () -> service.pack(3L,r)); }
    @Test void packagingReplayDoesNotConsumeWeightAgain() {
        lot.setStatus("GRADED"); HarvestRequests.Packing r=packing();
        doAnswer(call->{PackingLotEntity e=call.getArgument(0); e.setId(4L); assertEquals(new BigDecimal("35.000"),e.getTotalWeightKg()); when(mapper.packingByRequest(r.getRequestKey())).thenReturn(e); when(mapper.packing(4L)).thenReturn(e);return 1;}).when(mapper).insertPacking(any());
        service.pack(3L,r); when(mapper.packed(3L)).thenReturn(new BigDecimal("35")); service.pack(3L,r); verify(mapper,times(1)).insertPacking(any());
        r.setPackageCount(6); assertThrows(IllegalArgumentException.class, () -> service.pack(3L,r));
    }
    @Test void packingHistoricalSourceDoesNotRequireActiveCycle() { lot.setStatus("GRADED"); cycle.setStatus("ENDED"); service.pack(3L,packing()); verify(access,never()).active(any()); verify(access).cycle(7L,true); }
    @Test void rejectsWeightOverflow() { assertThrows(IllegalArgumentException.class, () -> HarvestService.toKg(new BigDecimal("1000000000"),"kg")); }
}
