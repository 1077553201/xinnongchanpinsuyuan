package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.LossCorrectionRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class DispatchCorrectionServiceTest {
    private final InventoryMapper inventory=mock(InventoryMapper.class);private final LossCorrectionMapper dependencies=mock(LossCorrectionMapper.class);private final DispatchCorrectionMapper records=mock(DispatchCorrectionMapper.class);private final SalesMapper sales=mock(SalesMapper.class);
    private final DispatchCorrectionService service=new DispatchCorrectionService(inventory,dependencies,new PayloadService(),mock(AuditService.class),records,sales);
    private final Actor actor=new Actor();private final InventoryMovementEntity original=new InventoryMovementEntity();private final InventorySource source=new InventorySource();private final SalesLineEntity line=new SalesLineEntity();private final SalesOrderEntity order=new SalesOrderEntity();private final SalesDispatchEntity dispatch=new SalesDispatchEntity();private final List<LossCorrectionEntity> history=new ArrayList<>();private int balance=3;
    @BeforeEach void setup(){
        actor.setId(1L);actor.setRole("MANAGER");actor.setOrganizationId(3L);AccessContext.set(actor);
        original.setId(7L);original.setPackingId(5L);original.setWarehouseId(9L);original.setKind("OUT");original.setPackageCount(2);original.setPackageUnit("盒");original.setWeightKg(new BigDecimal("2.000"));original.setOccurredAt(LocalDateTime.now().minusDays(1).withNano(0));when(inventory.movement(7L)).thenReturn(original);
        source.setId(5L);source.setOrganizationId(3L);source.setPackageCount(5);source.setReceivedCount(5);source.setAvailableCount(3);source.setPackageUnit("盒");source.setNetWeightKg(BigDecimal.ONE);when(inventory.source(5L)).thenReturn(source);when(inventory.balance(5L,9L)).thenAnswer(c->balance);when(inventory.positions(5L)).thenReturn(List.of());WarehouseEntity w=new WarehouseEntity();w.setOrganizationId(3L);when(inventory.warehouse(9L)).thenReturn(w);
        dispatch.setId(10L);dispatch.setLineId(8L);dispatch.setMovementId(7L);when(records.dispatch(7L)).thenReturn(dispatch);when(records.latestMovement(5L,9L)).thenReturn(7L);
        line.setId(8L);line.setOrderId(11L);line.setPackingId(5L);line.setWarehouseId(9L);line.setPackageCount(2);line.setDispatchedCount(2);when(sales.line(8L)).thenReturn(line);when(sales.lineOrderId(8L)).thenReturn(11L);when(sales.lines(11L)).thenAnswer(c->List.of(line));order.setId(11L);order.setOrganizationId(3L);order.setStatus("FULFILLED");when(sales.order(11L)).thenReturn(order);
        when(records.history(7L)).thenAnswer(c->new ArrayList<>(history));when(dependencies.orders(5L)).thenReturn(List.of());when(dependencies.codes(5L)).thenReturn(List.of());when(dependencies.evidence(5L)).thenReturn(List.of());
        doAnswer(c->{LossCorrectionEntity row=c.getArgument(0);row.setId((long)history.size()+1);history.add(row);balance=row.getBalanceAfter().intValue();source.setAvailableCount(balance);line.setDispatchedCount(line.getDispatchedCount()-row.getPreviousCount()+row.getCorrectedCount());when(records.byKey(row.getRequestKey())).thenReturn(row);return 1;}).when(records).insert(any());
        doAnswer(c->{order.setStatus(c.getArgument(1));return 1;}).when(sales).status(eq(11L),anyString());
    }
    @AfterEach void clear(){AccessContext.clear();}
    private LossCorrectionRequest request(int count){LossCorrectionRequest r=new LossCorrectionRequest();r.setCorrectedCount(count);r.setExpectedImpactHash((String)service.preview(7L,count).get("impactHash"));r.setRequestKey(UUID.randomUUID().toString());r.setVerifiedAt(LocalDateTime.now().minusMinutes(1).withNano(0));r.setPerformerName("QA 核实");r.setReason("原销售出库数量误录，实物尚未交付");return r;}
    @Test void correctedQuantityBalancesStockLineRemainderAndAllOrderStates(){service.confirm(7L,request(1));assertEquals(4,balance);assertEquals(1,line.getDispatchedCount());assertEquals("PARTIAL",order.getStatus());service.confirm(7L,request(0));assertEquals(5,balance);assertEquals("OPEN",order.getStatus());service.confirm(7L,request(2));assertEquals(3,balance);assertEquals("FULFILLED",order.getStatus());assertEquals(2,original.getPackageCount());verify(inventory,never()).insertMovement(any());}
    @Test void previewShowsRemainderAndPredictedStatus(){Map<?,?> impact=(Map<?,?>)service.preview(7L,1).get("impact");assertEquals(0,impact.get("remainingBefore"));assertEquals(1L,impact.get("remainingAfter"));assertEquals("FULFILLED",impact.get("orderStatusBefore"));assertEquals("PARTIAL",impact.get("orderStatusAfter"));}
    @Test void quantityCannotExceedOrderedLineEvenWhenStockExists(){assertThrows(IllegalArgumentException.class,()->service.preview(7L,3));verify(records,never()).insert(any());}
    @Test void increasingDispatchCannotOverdrawWarehouse(){line.setPackageCount(5);assertThrows(IllegalArgumentException.class,()->service.preview(7L,6));}
    @Test void plannedShipmentAloneBlocksBothPreviewAndDirectConfirm(){LossCorrectionRequest r=request(1);when(records.shipmentCount(10L)).thenReturn(1L);assertEquals(false,service.preview(7L,1).get("canConfirm"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void individualLinksAndFrozenEvidenceAreIndependentBlockers(){when(records.linkedCount(10L)).thenReturn(1L);assertEquals(false,service.preview(7L,1).get("canConfirm"));when(records.linkedCount(10L)).thenReturn(0L);when(records.frozenCount(10L)).thenReturn(1L);assertEquals(false,service.preview(7L,1).get("canConfirm"));}
    @Test void laterWarehouseMovementAndStoppedOrderRemainBlocked(){LossCorrectionRequest r=request(1);when(records.latestMovement(5L,9L)).thenReturn(12L);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));when(records.latestMovement(5L,9L)).thenReturn(7L);for(String status:List.of("CLOSED","CANCELLED")){order.setStatus(status);assertEquals(false,service.preview(7L,1).get("canConfirm"));}}
    @Test void ordinaryOutCannotUseTheSalesCorrectionEndpoint(){when(records.dispatch(7L)).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.detail(7L));verify(records,never()).insert(any());}
    @Test void replayAfterShipmentCreationDoesNotWriteOrReopenOrder(){LossCorrectionRequest r=request(1);service.confirm(7L,r);when(records.shipmentCount(10L)).thenReturn(1L);service.confirm(7L,r);assertEquals(1,history.size());verify(sales,times(1)).status(anyLong(),anyString());r.setReason("different");assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));}
    @Test void newLineTotalsAndStockRequireFreshPreview(){LossCorrectionRequest r=request(1);line.setPackageCount(3);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));line.setPackageCount(2);balance=2;source.setAvailableCount(2);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void statusDependsOnAllLinesInsteadOfOnlyCurrentLine(){SalesLineEntity other=new SalesLineEntity();other.setId(12L);other.setPackageCount(1);other.setDispatchedCount(0);when(sales.lines(11L)).thenReturn(List.of(line,other));service.confirm(7L,request(1));service.confirm(7L,request(2));assertEquals("PARTIAL",order.getStatus());}
    @Test void permissionAndOrganizationBoundariesArePreserved(){LossCorrectionRequest r=request(1);actor.setRole("VIEWER");service.detail(7L);assertThrows(AccessException.class,()->service.confirm(7L,r));actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.detail(7L));}
    @Test void historyTamperingIsDetectedWithoutChangingOriginal(){service.confirm(7L,request(1));history.get(0).setReason("tampered");assertThrows(IllegalArgumentException.class,()->service.detail(7L));assertEquals(2,original.getPackageCount());}
    @Test void orderLockPrecedesPackingLockAndCurrentQuantityRead(){service.confirm(7L,request(1));org.mockito.InOrder sequence=inOrder(sales,inventory,records);sequence.verify(sales).lockOrder(11L);sequence.verify(inventory).lockPacking(5L);sequence.verify(records).byKey(anyString());sequence.verify(records).history(7L);}
    @Test void orderStatusFailureRollsBackTheJdbcTransaction() throws Exception {LossCorrectionRequest r=request(1);DataSource ds=mock(DataSource.class);Connection connection=mock(Connection.class);when(ds.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);doThrow(new IllegalStateException("状态更新失败")).when(sales).status(anyLong(),anyString());ProxyFactory factory=new ProxyFactory(service);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));DispatchCorrectionService proxy=(DispatchCorrectionService)factory.getProxy();assertThrows(IllegalStateException.class,()->proxy.confirm(7L,r));verify(connection).rollback();verify(connection,never()).commit();}
}
