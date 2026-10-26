package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class SalesReplacementServiceTest {
    private final SalesMapper sales=mock(SalesMapper.class);private final SalesReplacementMapper records=mock(SalesReplacementMapper.class);private final InventoryMapper inventory=mock(InventoryMapper.class);private final SalesService operations=mock(SalesService.class);
    private final SalesReplacementService service=new SalesReplacementService(sales,records,inventory,operations,new PayloadService(),mock(AuditService.class));
    private final Actor actor=new Actor();private final SalesOrderEntity original=new SalesOrderEntity(),replacement=new SalesOrderEntity();private final SalesLineEntity line=new SalesLineEntity();private final InventorySource source=new InventorySource();private SalesReplacementEntity saved;
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        original.setId(7L);original.setOrganizationId(3L);original.setStatus("OPEN");original.setOrderCode("QA-ORIGINAL");original.setCustomerName("原客户");original.setDeliveryAddress("原地址");original.setOrderedOn(LocalDate.now());original.setTotalAmount(new BigDecimal("6.00"));when(sales.order(7L)).thenReturn(original);when(sales.lockOrder(7L)).thenReturn(original);
        line.setId(8L);line.setOrderId(7L);line.setPackingId(5L);line.setWarehouseId(9L);line.setPackageCount(3);line.setUnitPrice(new BigDecimal("2.00"));line.setLineAmount(new BigDecimal("6.00"));line.setDispatchedCount(0);when(sales.lines(7L)).thenReturn(List.of(line));
        source.setId(5L);source.setOrganizationId(3L);source.setPackageCount(10);source.setPackageUnit("盒");source.setNetWeightKg(BigDecimal.ONE);source.setPackingCode("QA-PACK");when(inventory.source(5L)).thenReturn(source);when(inventory.balance(5L,9L)).thenReturn(5);
        WarehouseEntity warehouse=new WarehouseEntity();warehouse.setId(9L);warehouse.setOrganizationId(3L);when(inventory.warehouse(9L)).thenReturn(warehouse);
        replacement.setId(11L);replacement.setOrganizationId(3L);replacement.setStatus("OPEN");when(operations.create(any())).thenReturn(replacement);
        when(records.dependencies(7L)).thenReturn(List.of());when(records.relations(anyLong())).thenAnswer(c->saved==null?List.of():List.of(saved));
        doAnswer(c->{saved=c.getArgument(0);saved.setId(1L);when(records.byKey(saved.getRequestKey())).thenReturn(saved);when(records.outgoing(7L)).thenReturn(saved);return 1;}).when(records).insert(any());
        doAnswer(c->{original.setStatus("CANCELLED");return original;}).when(operations).close(eq(7L),any());
    }
    @AfterEach void clear(){AccessContext.clear();}
    private SalesReplacementRequests.Confirm request(){
        SalesRequests.Create target=new SalesRequests.Create();target.setOrganizationId(3L);target.setOrderCode("QA-REPLACEMENT");target.setOrderedOn(LocalDate.now());target.setCustomerName("更正客户");target.setCustomerContact("");target.setDeliveryAddress("更正地址");target.setNotes("");target.setRequestKey(UUID.randomUUID().toString());SalesRequests.Line item=new SalesRequests.Line();item.setPackingId(5L);item.setWarehouseId(9L);item.setPackageCount(2);item.setUnitPrice(new BigDecimal("3.50"));target.setLines(new ArrayList<>(List.of(item)));
        SalesReplacementRequests.Confirm r=new SalesReplacementRequests.Confirm();r.setReplacement(target);r.setReason("客户与数量误录");r.setRequestKey(UUID.randomUUID().toString());r.setExpectedImpactHash((String)service.preview(7L,r).get("impactHash"));return r;
    }
    @Test void previewUsesExactMoneyAndPreservesOriginal(){Map<?,?> impact=(Map<?,?>)service.preview(7L,request()).get("impact");assertEquals("7.00",impact.get("proposedAmount"));assertEquals(new BigDecimal("6.00"),original.getTotalAmount());assertEquals(3,line.getPackageCount());verifyNoInteractions(operations);}
    @Test void confirmationStopsOriginalCreatesOneTargetAndRetainsPreChangeSnapshot(){service.confirm(7L,request());assertEquals("CANCELLED",original.getStatus());assertEquals(7L,saved.getOriginalOrderId());assertEquals(11L,saved.getReplacementOrderId());assertTrue(saved.getImpactJson().contains("OPEN"));assertEquals(new PayloadService().payloadHash(saved.getImpactJson()),saved.getImpactHash());org.mockito.InOrder sequence=inOrder(sales,inventory,operations,records);sequence.verify(sales).lockOrder(7L);sequence.verify(inventory).lockPacking(5L);sequence.verify(operations).close(eq(7L),any());sequence.verify(operations).create(any());sequence.verify(records).insert(any());verify(inventory,never()).insertMovement(any());}
    @Test void dispatchedOrderCannotBeReplacedEvenIfStatusIncorrectlyOpen(){SalesReplacementRequests.Confirm r=request();when(records.dispatchCount(7L)).thenReturn(1L);assertEquals(false,service.preview(7L,r).get("canConfirm"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));verifyNoInteractions(operations);}
    @Test void cancelledClosedAndFulfilledStatesAreBlocked(){SalesReplacementRequests.Confirm r=request();for(String status:List.of("CANCELLED","CLOSED","PARTIAL","FULFILLED")){original.setStatus(status);assertEquals(false,service.preview(7L,r).get("canConfirm"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));}verifyNoInteractions(operations);}
    @Test void originalAndInventoryChangesRequireFreshConfirmation(){SalesReplacementRequests.Confirm r=request();when(inventory.balance(5L,9L)).thenReturn(4);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));when(inventory.balance(5L,9L)).thenReturn(5);original.setCustomerName("已变更");assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));verifyNoInteractions(operations);}
    @Test void editedProposedDetailsCannotReuseOldPreview(){SalesReplacementRequests.Confirm r=request();r.getReplacement().setDeliveryAddress("再次更正");assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));verifyNoInteractions(operations);}
    @Test void originalSourcesMustBeRetainedAndCannotDuplicate(){SalesReplacementRequests.Confirm r=request();r.getReplacement().getLines().get(0).setWarehouseId(88L);assertThrows(IllegalArgumentException.class,()->service.preview(7L,r));SalesReplacementRequests.Confirm duplicated=request();duplicated.getReplacement().getLines().add(duplicated.getReplacement().getLines().get(0));assertThrows(IllegalArgumentException.class,()->service.preview(7L,duplicated));}
    @Test void quantitiesAreCappedByPhysicalPackingNotCurrentAvailability(){SalesReplacementRequests.Confirm r=request();r.getReplacement().getLines().get(0).setPackageCount(11);assertThrows(IllegalArgumentException.class,()->service.preview(7L,r));r.getReplacement().getLines().get(0).setPackageCount(8);assertEquals(true,service.preview(7L,r).get("canConfirm"));}
    @Test void reusedNewOrderKeyOrCodeCannotAttachAnExistingOrder(){SalesReplacementRequests.Confirm r=request();when(sales.orderByRequest(r.getReplacement().getRequestKey())).thenReturn(replacement);assertEquals(false,service.preview(7L,r).get("canConfirm"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));when(sales.orderByRequest(anyString())).thenReturn(null);when(records.codeCount("QA-REPLACEMENT")).thenReturn(1L);assertEquals(false,service.preview(7L,r).get("canConfirm"));verifyNoInteractions(operations);}
    @Test void successfulRequestReplaysAfterReplacementHasBeenFulfilled(){SalesReplacementRequests.Confirm r=request();service.confirm(7L,r);replacement.setStatus("FULFILLED");service.confirm(7L,r);verify(operations,times(1)).create(any());verify(records,times(1)).insert(any());r.setReason("其他原因");assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));}
    @Test void secondReplacementCannotUseDifferentConfirmation(){SalesReplacementRequests.Confirm first=request(),second=request();service.confirm(7L,first);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,second));verify(operations,times(1)).create(any());}
    @Test void viewerCanReadButCannotConfirmAndOutsiderCannotInspect(){SalesReplacementRequests.Confirm r=request();actor.setRole("VIEWER");service.detail(7L);assertThrows(AccessException.class,()->service.confirm(7L,r));actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.detail(7L));}
    @Test void targetOrganizationAndWarehouseMustMatch(){SalesReplacementRequests.Confirm r=request();r.getReplacement().setOrganizationId(99L);assertThrows(IllegalArgumentException.class,()->service.preview(7L,r));r.getReplacement().setOrganizationId(3L);source.setOrganizationId(99L);assertThrows(IllegalArgumentException.class,()->service.preview(7L,r));}
    @Test void relationSnapshotAndMetadataTamperingAreDetected(){service.confirm(7L,request());saved.setReason("tampered");assertThrows(IllegalArgumentException.class,()->service.detail(7L));saved.setReason("客户与数量误录");saved.setImpactJson("{}");assertThrows(IllegalArgumentException.class,()->service.detail(7L));}
    @Test void fullDispatchCountBlocksEvenWhenDisplayedDependenciesAreTruncated(){when(records.dispatchCount(7L)).thenReturn(300L);assertEquals(true,service.detail(7L).get("dependenciesTruncated"));assertEquals(false,service.preview(7L,request()).get("canConfirm"));}
    @Test void businessCreateFailureRollsBackTheTransactionBoundary() throws Exception {
        SalesReplacementRequests.Confirm r=request();DataSource data=mock(DataSource.class);Connection connection=mock(Connection.class);when(data.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        ProxyFactory factory=new ProxyFactory(service);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));SalesReplacementService proxy=(SalesReplacementService)factory.getProxy();
        when(operations.create(any())).thenThrow(new IllegalArgumentException("创建失败"));assertThrows(IllegalArgumentException.class,()->proxy.confirm(7L,r));verify(connection).rollback();verify(connection,never()).commit();verify(records,never()).insert(any());
    }
}
