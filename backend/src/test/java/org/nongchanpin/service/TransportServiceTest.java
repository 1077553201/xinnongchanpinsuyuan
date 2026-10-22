package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.TransportRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.TransportModels.*;
import org.nongchanpin.security.*;

class TransportServiceTest {
    private final TransportMapper mapper=mock(TransportMapper.class);
    private final SalesMapper sales=mock(SalesMapper.class);
    private final TransportService service=new TransportService(mapper,sales,new PayloadService(),mock(AuditService.class));
    private final Shipment shipment=new Shipment();
    private final Actor actor=new Actor();
    @BeforeEach void setup() {
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");actor.setDisplayName("录入人");AccessContext.set(actor);
        shipment.setId(7L);shipment.setOrganizationId(3L);shipment.setPackageCount(3);shipment.setStatus("PLANNED");shipment.setDispatchedAt(LocalDateTime.now().minusHours(2));
        when(mapper.shipment(7L)).thenReturn(shipment);when(mapper.organization(7L)).thenReturn(3L);
        SalesDispatchEntity dispatch=new SalesDispatchEntity();dispatch.setId(9L);dispatch.setLineId(10L);when(sales.dispatch(9L)).thenReturn(dispatch);when(sales.lineOrderId(10L)).thenReturn(11L);
        SalesOrderEntity order=new SalesOrderEntity();order.setId(11L);order.setOrganizationId(3L);order.setCustomerName("原收货方");order.setDeliveryAddress("原收货地址");order.setStatus("CLOSED");when(sales.order(11L)).thenReturn(order);
    }
    @AfterEach void clear(){AccessContext.clear();}
    private TransportRequests.Create create(){TransportRequests.Create r=new TransportRequests.Create();r.setDispatchId(9L);r.setShipmentCode("SHIP-TEST");r.setCarrier("承运方");r.setDriverName("联系人");r.setRequestKey("00000000-0000-4000-8000-000000000001");return r;}
    private TransportRequests.Event event(String kind){TransportRequests.Event r=new TransportRequests.Event();r.setKind(kind);r.setOccurredAt(LocalDateTime.now().minusHours(1));r.setLocation("实际地点");r.setPerformerName("现场人员");r.setDescription("实际事实");r.setRequestKey("00000000-0000-4000-8000-000000000002");return r;}
    @Test void createUsesOriginalRecipientEvenWhenRemainingOrderStopped(){service.create(create());verify(mapper).insert(argThat(s->s.getRecipient().equals("原收货方")&&s.getDeliveryAddress().equals("原收货地址")));}
    @Test void duplicateDispatchCannotCreateSecondShipment(){when(mapper.byDispatch(9L)).thenReturn(shipment);assertThrows(IllegalArgumentException.class,()->service.create(create()));verify(mapper,never()).insert(any());}
    @Test void missingDispatchRejected(){TransportRequests.Create r=create();r.setDispatchId(999L);assertThrows(IllegalArgumentException.class,()->service.create(r));}
    @Test void createReplayOnlyReturnsOriginalAndRejectsChangedContent(){TransportRequests.Create r=create();doAnswer(c->{Shipment s=c.getArgument(0);s.setId(7L);when(mapper.byRequest(r.getRequestKey())).thenReturn(s);return 1;}).when(mapper).insert(any());service.create(r);service.create(r);verify(mapper,times(1)).insert(any());r.setCarrier("其他承运方");assertThrows(IllegalArgumentException.class,()->service.create(r));}
    @Test void plannedShipmentMustDepartBeforeReceipt(){TransportRequests.Event r=event("RECEIVE");r.setAcceptedCount(3);r.setRejectedCount(0);assertThrows(IllegalArgumentException.class,()->service.record(7L,r));}
    @Test void departTransitionsToInTransit(){service.record(7L,event("DEPART"));verify(mapper).status(7L,"IN_TRANSIT");}
    @Test void stateIsFirstReadAfterLock(){service.record(7L,event("DEPART"));org.mockito.InOrder sequence=inOrder(mapper);sequence.verify(mapper).organization(7L);sequence.verify(mapper).lock(7L);sequence.verify(mapper).shipment(7L);}
    @Test void cannotDepartTwice(){shipment.setStatus("IN_TRANSIT");assertThrows(IllegalArgumentException.class,()->service.record(7L,event("DEPART")));}
    @Test void actualTimesCannotPrecedeDispatchOrBeFuture(){TransportRequests.Event r=event("DEPART");r.setOccurredAt(shipment.getDispatchedAt().minusSeconds(1));assertThrows(IllegalArgumentException.class,()->service.record(7L,r));r.setOccurredAt(LocalDateTime.now().plusHours(1));assertThrows(IllegalArgumentException.class,()->service.record(7L,r));}
    @Test void timeCannotPrecedeLatestFact(){shipment.setStatus("IN_TRANSIT");Event latest=new Event();latest.setOccurredAt(LocalDateTime.now().minusMinutes(5));when(mapper.latest(7L)).thenReturn(latest);assertThrows(IllegalArgumentException.class,()->service.record(7L,event("UPDATE")));}
    @Test void exceptionRetainsInTransitWithoutInventingRecovery(){shipment.setStatus("IN_TRANSIT");service.record(7L,event("EXCEPTION"));verify(mapper).insertEvent(any());verify(mapper,never()).status(any(),any());}
    @Test void receiptRequiresBothCountsAndExactTotal(){shipment.setStatus("IN_TRANSIT");TransportRequests.Event r=event("RECEIVE");assertThrows(IllegalArgumentException.class,()->service.record(7L,r));r.setAcceptedCount(2);r.setRejectedCount(0);assertThrows(IllegalArgumentException.class,()->service.record(7L,r));verify(mapper,never()).insertEvent(any());}
    @Test void fullAcceptanceAndPartialRejectionHaveDistinctStatuses(){shipment.setStatus("IN_TRANSIT");TransportRequests.Event r=event("RECEIVE");r.setAcceptedCount(3);r.setRejectedCount(0);service.record(7L,r);verify(mapper).status(7L,"DELIVERED");r.setAcceptedCount(2);r.setRejectedCount(1);service.record(7L,r);verify(mapper).status(7L,"DELIVERY_ISSUE");}
    @Test void nonReceiptCannotCarryReceiptCounts(){TransportRequests.Event r=event("DEPART");r.setAcceptedCount(0);assertThrows(IllegalArgumentException.class,()->service.record(7L,r));}
    @Test void completedShipmentRejectsNewFactsButPermitsIdenticalReplay(){shipment.setStatus("IN_TRANSIT");TransportRequests.Event r=event("RECEIVE");r.setAcceptedCount(3);r.setRejectedCount(0);doAnswer(c->{Event e=c.getArgument(0);when(mapper.eventByRequest(r.getRequestKey())).thenReturn(e);return 1;}).when(mapper).insertEvent(any());service.record(7L,r);shipment.setStatus("DELIVERED");service.record(7L,r);verify(mapper,times(1)).insertEvent(any());r.setDescription("不同事实");assertThrows(IllegalArgumentException.class,()->service.record(7L,r));assertThrows(IllegalArgumentException.class,()->service.record(7L,event("UPDATE")));}
    @Test void organizationIsolationAndReadonlyPermissions(){actor.setOrganizationId(8L);assertThrows(AccessException.class,()->service.detail(7L));assertThrows(AccessException.class,()->service.create(create()));assertThrows(AccessException.class,()->service.record(7L,event("DEPART")));actor.setOrganizationId(3L);actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.create(create()));assertThrows(AccessException.class,()->service.record(7L,event("DEPART")));service.detail(7L);}
    @Test void validationRejectsNegativeCountsUnsupportedKindAndMissingFields(){try(javax.validation.ValidatorFactory factory=javax.validation.Validation.buildDefaultValidatorFactory()){TransportRequests.Event r=event("RECEIVE");r.setAcceptedCount(-1);assertFalse(factory.getValidator().validate(r).isEmpty());r.setAcceptedCount(1);r.setKind("FAKE");assertFalse(factory.getValidator().validate(r).isEmpty());TransportRequests.Create c=create();c.setCarrier(" ");assertFalse(factory.getValidator().validate(c).isEmpty());}}
}
