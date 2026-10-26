package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.TransportModels.Shipment;
import org.nongchanpin.security.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class TransportPlanServiceTest {
    private final TransportMapper transport=mock(TransportMapper.class);private final TransportPlanMapper plans=mock(TransportPlanMapper.class);private final SalesMapper sales=mock(SalesMapper.class);private final InventoryMapper inventory=mock(InventoryMapper.class);
    private final TransportPlanService service=new TransportPlanService(transport,plans,sales,inventory,new PayloadService(),mock(AuditService.class));
    private final Shipment original=new Shipment(),current=new Shipment();private final SalesDispatchEntity dispatch=new SalesDispatchEntity();private final List<TransportPlanAction> history=new ArrayList<>();private final Actor actor=new Actor();
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        original.setId(7L);original.setDispatchId(15L);original.setShipmentCode("ORIGINAL");original.setCarrier("原承运方");original.setDriverName("原联系人");original.setDriverContact("");original.setTrackingReference("");original.setNotes("");original.setPackageCount(1);original.setPackageUnit("盒");original.setRecipient("私有收货人");original.setDeliveryAddress("私有地址");original.setRecordedBy(1L);original.setRecordedAt(LocalDateTime.now().minusDays(1).withNano(0));org.springframework.beans.BeanUtils.copyProperties(original,current);current.setOrganizationId(3L);current.setOrderId(11L);current.setPackingId(5L);current.setStatus("PLANNED");
        when(transport.shipment(7L)).thenReturn(current);when(plans.original(7L)).thenReturn(original);when(plans.history(7L)).thenAnswer(c->new ArrayList<>(history));when(plans.codeCount("ORIGINAL")).thenReturn(1L);
        dispatch.setId(15L);dispatch.setPackageCount(1);dispatch.setPackageUnit("盒");when(sales.dispatch(15L)).thenReturn(dispatch);
        doAnswer(c->{TransportPlanAction row=c.getArgument(0);row.setId((long)history.size()+1);history.add(row);when(plans.byKey(row.getRequestKey())).thenReturn(row);if("REPLACE".equals(row.getAction())){current.setShipmentCode(row.getShipmentCode());current.setCarrier(row.getCarrier());current.setTrackingReference(row.getTrackingReference());current.setDriverName(row.getDriverName());current.setDriverContact(row.getDriverContact());current.setNotes(row.getNotes());current.setPackageCount(row.getPackageCount());current.setPackageUnit(row.getPackageUnit());current.setCorrectionRecordedAt(row.getCorrectionRecordedAt());}return 1;}).when(plans).insert(any());
        doAnswer(c->{current.setStatus(c.getArgument(1));return 1;}).when(transport).status(eq(7L),anyString());
    }
    @AfterEach void clear(){AccessContext.clear();}
    private TransportPlanRequests.Confirm request(String action){TransportPlanRequests.Confirm r=new TransportPlanRequests.Confirm();r.setAction(action);r.setReason("核实待发运安排需更正");r.setRequestKey(UUID.randomUUID().toString());if("REPLACE".equals(action)){TransportRequests.Create d=new TransportRequests.Create();d.setDispatchId(15L);d.setShipmentCode("NEW-"+UUID.randomUUID());d.setCarrier("新承运方");d.setDriverName("新联系人");d.setRequestKey(UUID.randomUUID().toString());r.setReplacement(d);}r.setExpectedImpactHash((String)service.preview(7L,r).get("impactHash"));return r;}
    @Test void cancelAndReplaceRetainOriginalPlanAndUseStableShipmentIdentity(){service.confirm(7L,request("CANCEL"));assertEquals("CANCELLED",current.getStatus());service.confirm(7L,request("REPLACE"));assertEquals("PLANNED",current.getStatus());assertEquals(7L,current.getId());assertEquals("ORIGINAL",original.getShipmentCode());assertEquals("原承运方",original.getCarrier());assertEquals(2,history.size());verify(inventory,never()).insertMovement(any());verify(sales,never()).status(any(),any());}
    @Test void archivedPlanQuantityDoesNotDriftWhenCancelledDispatchChanges(){service.confirm(7L,request("CANCEL"));dispatch.setPackageCount(2);assertEquals(1,current.getPackageCount());service.confirm(7L,request("REPLACE"));assertEquals(2,current.getPackageCount());assertEquals(1,original.getPackageCount());}
    @Test void replaceRequiresPriorCancellationAndNoNewQuantityWhenZero(){assertEquals(false,service.preview(7L,request("REPLACE")).get("canConfirm"));service.confirm(7L,request("CANCEL"));dispatch.setPackageCount(0);assertEquals(false,service.preview(7L,request("REPLACE")).get("canConfirm"));assertEquals(1,history.size());}
    @Test void repeatedCancellationAndWrongStateDoNotAppend(){TransportPlanRequests.Confirm r=request("CANCEL");service.confirm(7L,r);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,request("CANCEL")));assertEquals(1,history.size());}
    @Test void physicalEventsBlockRegardlessOfStateLabel(){TransportPlanRequests.Confirm r=request("CANCEL");when(plans.eventCount(7L)).thenReturn(1L);assertEquals(false,service.preview(7L,r).get("canConfirm"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void departureAndReceiptStatesAreNeverTreatableAsPlanned(){for(String state:List.of("IN_TRANSIT","DELIVERED","DELIVERY_ISSUE")){current.setStatus(state);assertEquals(false,service.preview(7L,request("CANCEL")).get("canConfirm"));}assertTrue(history.isEmpty());}
    @Test void packageLinksAndFrozenEvidenceAreIndependentBlockers(){when(plans.linkedCount(15L)).thenReturn(1L);assertEquals(false,service.preview(7L,request("CANCEL")).get("canConfirm"));when(plans.linkedCount(15L)).thenReturn(0L);when(plans.frozenCount(15L)).thenReturn(1L);assertEquals(false,service.preview(7L,request("CANCEL")).get("canConfirm"));}
    @Test void codesCannotReuseOriginalOrAnyHistoricalPlanCode(){service.confirm(7L,request("CANCEL"));TransportPlanRequests.Confirm r=request("REPLACE");r.getReplacement().setShipmentCode("ORIGINAL");assertEquals(false,service.preview(7L,r).get("canConfirm"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));}
    @Test void replacementCannotChangeDispatchIdentity(){service.confirm(7L,request("CANCEL"));TransportPlanRequests.Confirm r=request("REPLACE");r.getReplacement().setDispatchId(99L);assertThrows(IllegalArgumentException.class,()->service.preview(7L,r));}
    @Test void successfulRequestReplaysAfterLaterReplacementOrShipmentProgress(){TransportPlanRequests.Confirm r=request("CANCEL");service.confirm(7L,r);service.confirm(7L,request("REPLACE"));current.setStatus("IN_TRANSIT");service.confirm(7L,r);assertEquals(2,history.size());r.setReason("changed");assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));}
    @Test void sourceQuantityChangeRequiresNewPreview(){TransportPlanRequests.Confirm r=request("CANCEL");dispatch.setPackageCount(2);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void stateIsReadAgainAfterLocking(){TransportPlanRequests.Confirm r=request("CANCEL");doAnswer(c->{current.setStatus("IN_TRANSIT");return 7L;}).when(transport).lock(7L);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void lockingOrderMatchesOrderPackingShipmentHierarchy(){service.confirm(7L,request("CANCEL"));org.mockito.InOrder sequence=inOrder(sales,inventory,transport,plans);sequence.verify(sales).lockOrder(11L);sequence.verify(inventory).lockPacking(5L);sequence.verify(transport).lock(7L);sequence.verify(plans).byKey(anyString());}
    @Test void originalMetadataAndActionHistoryAreHashChecked(){service.confirm(7L,request("CANCEL"));original.setCarrier("tampered");assertThrows(IllegalArgumentException.class,()->service.detail(7L));original.setCarrier("原承运方");history.get(0).setReason("tampered");assertThrows(IllegalArgumentException.class,()->service.detail(7L));}
    @Test void livePlanCannotDisagreeWithItsRetainedVersion(){service.confirm(7L,request("CANCEL"));service.confirm(7L,request("REPLACE"));current.setPackageCount(9);assertThrows(IllegalArgumentException.class,()->service.detail(7L));}
    @Test void viewerCanReadAndOutsiderCannotAccess(){TransportPlanRequests.Confirm r=request("CANCEL");actor.setRole("VIEWER");service.detail(7L);assertThrows(AccessException.class,()->service.confirm(7L,r));actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.detail(7L));}
    @Test void cancelledRootDoesNotAcceptPhysicalEventsByStateMismatch(){service.confirm(7L,request("CANCEL"));current.setStatus("IN_TRANSIT");assertThrows(IllegalArgumentException.class,()->service.detail(7L));}
    @Test void noPlanEditsCanBeSmuggledIntoCancellation(){TransportPlanRequests.Confirm r=request("CANCEL"),newPlan=request("REPLACE");r.setReplacement(newPlan.getReplacement());assertThrows(IllegalArgumentException.class,()->service.preview(7L,r));}
    @Test void replacementStateFailureRollsBackTheJdbcTransaction() throws Exception {service.confirm(7L,request("CANCEL"));TransportPlanRequests.Confirm r=request("REPLACE");DataSource ds=mock(DataSource.class);Connection connection=mock(Connection.class);when(ds.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);doThrow(new IllegalStateException("状态更新失败")).when(transport).status(7L,"PLANNED");ProxyFactory factory=new ProxyFactory(service);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));TransportPlanService proxy=(TransportPlanService)factory.getProxy();assertThrows(IllegalStateException.class,()->proxy.confirm(7L,r));verify(connection).rollback();verify(connection,never()).commit();}
}
