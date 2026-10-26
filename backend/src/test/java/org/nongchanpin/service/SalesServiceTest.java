package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;

class SalesServiceTest {
    private final SalesMapper mapper=mock(SalesMapper.class);
    private final InventoryMapper inventory=mock(InventoryMapper.class);
    private final InventoryService stock=mock(InventoryService.class);
    private final ProductionMapper production=mock(ProductionMapper.class);
    private final SalesService service=new SalesService(mapper,inventory,stock,production,new PayloadService(),mock(AuditService.class));
    private final SalesOrderEntity order=new SalesOrderEntity();
    private final SalesLineEntity line=new SalesLineEntity();
    private final InventorySource source=new InventorySource();
    private final WarehouseEntity warehouse=new WarehouseEntity();
    private final Actor actor=new Actor();
    @BeforeEach void setup() {
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");actor.setDisplayName("录入人");AccessContext.set(actor);
        source.setId(5L);source.setOrganizationId(3L);source.setPackageCount(10);source.setNetWeightKg(new BigDecimal("2.500"));source.setPackingCode("PACK");source.setPackageUnit("箱");source.setProductName("草莓");source.setVariety("红颜");
        warehouse.setId(11L);warehouse.setOrganizationId(3L);warehouse.setWarehouseName("冷库A");when(inventory.source(5L)).thenReturn(source);when(inventory.warehouse(11L)).thenReturn(warehouse);when(production.activeOrganization(3L)).thenReturn(1);
        order.setId(7L);order.setOrganizationId(3L);order.setOrderCode("ORDER");order.setStatus("OPEN");order.setCustomerName("测试客户");order.setOrderedOn(LocalDate.now().minusDays(1));when(mapper.order(7L)).thenReturn(order);when(mapper.lockOrder(7L)).thenReturn(order);
        line.setId(9L);line.setOrderId(7L);line.setPackingId(5L);line.setWarehouseId(11L);line.setPackageCount(3);line.setDispatchedCount(0);when(mapper.lineOrderId(9L)).thenReturn(7L);when(mapper.line(9L)).thenReturn(line);when(mapper.lines(7L)).thenReturn(List.of(line));
        InventoryMovementEntity moved=new InventoryMovementEntity();moved.setId(88L);when(stock.record(eq(5L),any())).thenReturn(moved);
    }
    @AfterEach void clear(){AccessContext.clear();}
    private SalesRequests.Create create() {
        SalesRequests.Create r=new SalesRequests.Create();r.setOrganizationId(3L);r.setOrderCode("ORDER-TEST");r.setOrderedOn(LocalDate.now());r.setCustomerName("客户");r.setDeliveryAddress("测试地址");r.setRequestKey("00000000-0000-4000-8000-000000000001");
        SalesRequests.Line l=new SalesRequests.Line();l.setPackingId(5L);l.setWarehouseId(11L);l.setPackageCount(3);l.setUnitPrice(new BigDecimal("0.10"));r.setLines(new ArrayList<>(List.of(l)));return r;
    }
    private SalesRequests.Dispatch dispatch(int count){SalesRequests.Dispatch r=new SalesRequests.Dispatch();r.setPackageCount(count);r.setOccurredAt(LocalDateTime.now().minusMinutes(1));r.setPerformerName("现场人员");r.setReason("实际销售出库");r.setRequestKey("00000000-0000-4000-8000-000000000002");return r;}
    @Test void createCalculatesExactAmountsWithoutDeductingStock(){doAnswer(c->{SalesOrderEntity e=c.getArgument(0);e.setId(7L);assertEquals(new BigDecimal("0.30"),e.getTotalAmount());return 1;}).when(mapper).insertOrder(any());service.create(create());verify(mapper).insertLine(any());verifyNoInteractions(stock);}
    @Test void orderReplayRetainsOriginalWithoutNewLines(){SalesRequests.Create r=create();doAnswer(c->{SalesOrderEntity e=c.getArgument(0);e.setId(7L);when(mapper.orderByRequest(r.getRequestKey())).thenReturn(e);return 1;}).when(mapper).insertOrder(any());service.create(r);service.create(r);verify(mapper,times(1)).insertOrder(any());r.setCustomerName("不同客户");assertThrows(IllegalArgumentException.class,()->service.create(r));}
    @Test void rejectsDuplicateSourcePair(){SalesRequests.Create r=create();r.getLines().add(r.getLines().get(0));assertThrows(IllegalArgumentException.class,()->service.create(r));}
    @Test void rejectsCrossOrganizationSources(){source.setOrganizationId(9L);assertThrows(IllegalArgumentException.class,()->service.create(create()));}
    @Test void rejectsCrossOrganizationWarehouse(){warehouse.setOrganizationId(9L);assertThrows(IllegalArgumentException.class,()->service.create(create()));}
    @Test void rejectsOrderBeyondActualPackingQuantity(){SalesRequests.Create r=create();r.getLines().get(0).setPackageCount(11);assertThrows(IllegalArgumentException.class,()->service.create(r));}
    @Test void rejectsFutureOrderDate(){SalesRequests.Create r=create();r.setOrderedOn(LocalDate.now().plusDays(1));assertThrows(IllegalArgumentException.class,()->service.create(r));}
    @Test void validatesNegativePriceAndNullLine(){try(javax.validation.ValidatorFactory factory=javax.validation.Validation.buildDefaultValidatorFactory()){SalesRequests.Create r=create();r.getLines().get(0).setUnitPrice(new BigDecimal("-1"));assertFalse(factory.getValidator().validate(r).isEmpty());r.getLines().set(0,null);assertFalse(factory.getValidator().validate(r).isEmpty());}}
    @Test void readonlyCannotCreateOrDispatch(){actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.create(create()));assertThrows(AccessException.class,()->service.dispatch(9L,dispatch(1)));}
    @Test void otherOrganizationCannotReadOrder(){actor.setOrganizationId(9L);assertThrows(AccessException.class,()->service.detail(7L));verify(mapper,never()).lines(any());}
    @Test void dispatchUsesFixedWarehouseCustomerAndOneInventoryWrite(){service.dispatch(9L,dispatch(1));verify(stock).record(eq(5L),argThat(r->r.getWarehouseId().equals(11L)&&r.getKind().equals("OUT")&&r.getDestination().equals("测试客户")&&r.getPackageCount()==1));verify(mapper).status(7L,"PARTIAL");}
    @Test void dispatchCannotExceedOrderRemainder(){line.setDispatchedCount(2);assertThrows(IllegalArgumentException.class,()->service.dispatch(9L,dispatch(2)));verifyNoInteractions(stock);}
    @Test void firstQuantityReadOccursOnlyAfterOrderLock(){service.dispatch(9L,dispatch(1));org.mockito.InOrder sequence=inOrder(mapper);sequence.verify(mapper).lineOrderId(9L);sequence.verify(mapper).order(7L);sequence.verify(mapper).lockOrder(7L);sequence.verify(mapper).line(9L);}
    @Test void closedOrderCannotDispatch(){order.setStatus("CLOSED");assertThrows(IllegalArgumentException.class,()->service.dispatch(9L,dispatch(1)));verifyNoInteractions(stock);}
    @Test void dispatchCannotPrecedeOrderDate(){SalesRequests.Dispatch r=dispatch(1);r.setOccurredAt(order.getOrderedOn().minusDays(1).atStartOfDay());assertThrows(IllegalArgumentException.class,()->service.dispatch(9L,r));}
    @Test void insufficientStockDoesNotCreateFulfillmentRecord(){when(stock.record(eq(5L),any())).thenThrow(new IllegalArgumentException("库存不足"));assertThrows(IllegalArgumentException.class,()->service.dispatch(9L,dispatch(1)));verify(mapper,never()).insertDispatch(any());}
    @Test void fullyDispatchedOrderAndRetryDeductOnce(){SalesRequests.Dispatch r=dispatch(3);doAnswer(c->{SalesDispatchEntity e=c.getArgument(0);e.setId(4L);line.setDispatchedCount(3);when(mapper.dispatchByRequest(r.getRequestKey())).thenReturn(e);when(mapper.dispatch(4L)).thenReturn(e);return 1;}).when(mapper).insertDispatch(any());service.dispatch(9L,r);verify(mapper).status(7L,"FULFILLED");order.setStatus("FULFILLED");service.dispatch(9L,r);verify(stock,times(1)).record(eq(5L),any());r.setPackageCount(2);assertThrows(IllegalArgumentException.class,()->service.dispatch(9L,r));}
    @Test void stoppingPartialOrderPreservesAlreadyDispatchedStock(){order.setStatus("PARTIAL");GrowingRequests.Reason r=new GrowingRequests.Reason();r.setReason("停止剩余");service.close(7L,r);assertEquals("CLOSED",order.getStatus());verifyNoInteractions(stock);}
    @Test void cancellationBeforeDispatchDoesNotInventInventory(){GrowingRequests.Reason r=new GrowingRequests.Reason();r.setReason("客户取消");service.close(7L,r);assertEquals("CANCELLED",order.getStatus());service.close(7L,r);verify(mapper,times(1)).close(any());verifyNoInteractions(stock);}
    @Test void fulfilledOrderCannotBeCancelledAsReturn(){order.setStatus("FULFILLED");GrowingRequests.Reason r=new GrowingRequests.Reason();r.setReason("不应退库");assertThrows(IllegalArgumentException.class,()->service.close(7L,r));}
    @Test void listChecksPaginationAndScope(){service.list("", "",1,20);verify(mapper).count(3L,"","");assertThrows(IllegalArgumentException.class,()->service.list("","invalid",1,20));assertThrows(IllegalArgumentException.class,()->service.list("","",0,20));}
    @Test void newOrderLocksPackagingInAscendingOrderBeforeCreatingDependency(){
        SalesRequests.Create request=create();SalesRequests.Line extra=new SalesRequests.Line();extra.setPackingId(6L);extra.setWarehouseId(11L);extra.setPackageCount(1);extra.setUnitPrice(new BigDecimal("0.10"));request.getLines().add(0,extra);
        InventorySource other=new InventorySource();other.setId(6L);other.setOrganizationId(3L);other.setPackageCount(10);other.setNetWeightKg(BigDecimal.ONE);when(inventory.source(6L)).thenReturn(other);
        doAnswer(c->{SalesOrderEntity row=c.getArgument(0);row.setId(7L);return 1;}).when(mapper).insertOrder(any());service.create(request);
        org.mockito.InOrder sequence=inOrder(inventory,mapper);sequence.verify(inventory).lockPacking(5L);sequence.verify(inventory).lockPacking(6L);sequence.verify(mapper).insertOrder(any());verifyNoInteractions(stock);
    }
}
