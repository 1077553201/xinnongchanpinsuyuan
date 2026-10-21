package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.InventoryMapper;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.dto.InventoryRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;

class InventoryServiceTest {
    private final InventoryMapper mapper = mock(InventoryMapper.class);
    private final ProductionMapper production = mock(ProductionMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private final InventoryService service = new InventoryService(mapper,production,new GrowingAccess(production),new PayloadService(),audit);
    private final InventorySource source = new InventorySource();
    private final WarehouseEntity warehouse = new WarehouseEntity();
    private final Actor actor = new Actor();
    private final PlantingCycleEntity cycle = new PlantingCycleEntity();
    @BeforeEach void setup() {
        actor.setId(1L); actor.setOrganizationId(3L); actor.setDisplayName("录入人"); actor.setRole("MANAGER"); AccessContext.set(actor);
        cycle.setId(7L); cycle.setPlotId(2L); cycle.setStatus("ACTIVE");
        FarmPlotEntity plot=new FarmPlotEntity(); plot.setId(2L); plot.setOrganizationId(3L);
        when(production.cycle(7L)).thenReturn(cycle); when(production.lockCycle(7L)).thenReturn(cycle); when(production.plot(2L)).thenReturn(plot); when(production.activeOrganization(3L)).thenReturn(1);
        source.setId(5L); source.setCycleId(7L); source.setOrganizationId(3L); source.setPackageCount(10); source.setPackageUnit("箱"); source.setNetWeightKg(new BigDecimal("2.500")); source.setPackedAt(LocalDateTime.now().minusHours(2));
        when(mapper.source(5L)).thenReturn(source); when(mapper.lockPacking(5L)).thenReturn(source);
        warehouse.setId(11L); warehouse.setOrganizationId(3L); warehouse.setWarehouseName("冷库 A"); when(mapper.warehouse(11L)).thenReturn(warehouse);
    }
    @AfterEach void clear() { AccessContext.clear(); }
    private InventoryRequests.Movement movement(String kind) {
        InventoryRequests.Movement r=new InventoryRequests.Movement(); r.setWarehouseId(11L); r.setMovementCode("STOCK-TEST"); r.setKind(kind); r.setPackageCount(3); r.setOccurredAt(LocalDateTime.now().minusHours(1)); r.setPerformerName("现场人员"); r.setReason("实际办理说明"); r.setDestination("测试接收方"); r.setRequestKey("00000000-0000-4000-8000-000000000001"); return r;
    }
    private InventoryRequests.Warehouse warehouseRequest() {
        InventoryRequests.Warehouse r=new InventoryRequests.Warehouse(); r.setOrganizationId(3L); r.setWarehouseCode("WAREHOUSE-TEST"); r.setWarehouseName("冷库"); r.setLocation("东侧"); return r;
    }
    @Test void recordsFixedSourceWeightAndSeparatePeople() {
        doAnswer(call->{InventoryMovementEntity e=call.getArgument(0); assertEquals(new BigDecimal("7.500"),e.getWeightKg()); assertEquals("箱",e.getPackageUnit()); assertEquals("现场人员",e.getPerformerName()); assertEquals("录入人",e.getRecorderName()); return 1;}).when(mapper).insertMovement(any());
        service.record(5L,movement("IN")); verify(mapper).lockPacking(5L); verify(production).lockPlot(2L);
    }
    @Test void cannotReceiveBeyondPackaging() { when(mapper.received(5L)).thenReturn(8); assertThrows(IllegalArgumentException.class,()->service.record(5L,movement("IN"))); verify(mapper,never()).insertMovement(any()); }
    @Test void exactRemainingReceiptIsAllowed() { when(mapper.received(5L)).thenReturn(7); service.record(5L,movement("IN")); verify(mapper).insertMovement(any()); }
    @Test void outCannotUseOtherWarehouseStock() { when(mapper.balance(5L,11L)).thenReturn(2); assertThrows(IllegalArgumentException.class,()->service.record(5L,movement("OUT"))); }
    @Test void lossCannotOverdraw() { when(mapper.balance(5L,11L)).thenReturn(2); assertThrows(IllegalArgumentException.class,()->service.record(5L,movement("LOSS"))); }
    @Test void outNeedsDestination() { when(mapper.balance(5L,11L)).thenReturn(10); InventoryRequests.Movement r=movement("OUT"); r.setDestination(" "); assertThrows(IllegalArgumentException.class,()->service.record(5L,r)); }
    @Test void exactBalanceCanBeDispatched() { when(mapper.balance(5L,11L)).thenReturn(3); service.record(5L,movement("OUT")); verify(mapper).insertMovement(any()); }
    @Test void cannotUseWarehouseOfDifferentOrganization() { warehouse.setOrganizationId(9L); assertThrows(IllegalArgumentException.class,()->service.record(5L,movement("IN"))); }
    @Test void cannotUseMissingWarehouse() { when(mapper.warehouse(11L)).thenReturn(null); assertThrows(IllegalArgumentException.class,()->service.record(5L,movement("IN"))); }
    @Test void cannotMoveBeforePacking() { InventoryRequests.Movement r=movement("IN"); r.setOccurredAt(source.getPackedAt().minusSeconds(1)); assertThrows(IllegalArgumentException.class,()->service.record(5L,r)); }
    @Test void cannotMoveInFuture() { InventoryRequests.Movement r=movement("IN"); r.setOccurredAt(LocalDateTime.now().plusHours(1)); assertThrows(IllegalArgumentException.class,()->service.record(5L,r)); }
    @Test void cannotRewriteHistoricalBalanceWithBackdatedMovement() { when(mapper.latestTime(5L,11L)).thenReturn(LocalDateTime.now().minusMinutes(30)); assertThrows(IllegalArgumentException.class,()->service.record(5L,movement("IN"))); }
    @Test void replayDoesNotReceiveAgainEvenWhenAllAlreadyReceived() {
        InventoryRequests.Movement request=movement("IN");
        doAnswer(call->{InventoryMovementEntity e=call.getArgument(0); e.setId(4L); when(mapper.byRequest(request.getRequestKey())).thenReturn(e); when(mapper.movement(4L)).thenReturn(e); return 1;}).when(mapper).insertMovement(any());
        service.record(5L,request); when(mapper.received(5L)).thenReturn(10); assertNotNull(service.record(5L,request)); verify(mapper,times(1)).insertMovement(any());
        request.setPackageCount(4); assertThrows(IllegalArgumentException.class,()->service.record(5L,request));
    }
    @Test void endedCycleStillAllowsInventory() { cycle.setStatus("ENDED"); service.record(5L,movement("IN")); verify(mapper).insertMovement(any()); }
    @Test void readonlyCannotWriteButCanReadSource() { actor.setRole("VIEWER"); assertThrows(AccessException.class,()->service.record(5L,movement("IN"))); service.sources(7L); verify(mapper).sources(7L); }
    @Test void otherOrganizationCannotInspectSource() { actor.setOrganizationId(9L); assertThrows(AccessException.class,()->service.detail(5L)); verify(mapper,never()).movements(any()); }
    @Test void warehouseListIsScoped() { service.warehouses(); verify(mapper).warehouses(3L); }
    @Test void warehouseCreationChecksOrganization() { InventoryRequests.Warehouse r=warehouseRequest(); r.setOrganizationId(9L); assertThrows(AccessException.class,()->service.createWarehouse(r)); }
    @Test void warehouseCreationChecksOrganizationActive() { when(production.activeOrganization(3L)).thenReturn(0); assertThrows(IllegalArgumentException.class,()->service.createWarehouse(warehouseRequest())); }
    @Test void warehouseCreationRetainsActualLocation() { doAnswer(call->{WarehouseEntity e=call.getArgument(0); assertEquals("东侧",e.getLocation()); assertEquals(1L,e.getCreatedBy()); return 1;}).when(mapper).insertWarehouse(any()); service.createWarehouse(warehouseRequest()); verify(mapper).insertWarehouse(any()); }
}
