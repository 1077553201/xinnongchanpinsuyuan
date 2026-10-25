package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.ItemCirculationRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.ItemCirculationModels.*;
import org.nongchanpin.model.entity.TransportModels.*;
import org.nongchanpin.security.*;

class ItemCirculationServiceTest {
    private final ItemCirculationMapper items=mock(ItemCirculationMapper.class);
    private final SalesMapper sales=mock(SalesMapper.class);
    private final PackingTraceMapper packing=mock(PackingTraceMapper.class);
    private final TransportMapper transport=mock(TransportMapper.class);
    private final ItemCirculationService service=new ItemCirculationService(items,sales,packing,transport,new PayloadService(),mock(AuditService.class));
    private final Actor actor=new Actor();
    private final Item item=new Item();
    private final Shipment shipment=new Shipment();
    private final Event receipt=new Event();
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        SalesDispatchEntity dispatch=new SalesDispatchEntity();dispatch.setId(9L);dispatch.setLineId(10L);dispatch.setPackageCount(2);dispatch.setOccurredAt(LocalDateTime.of(2026,10,20,10,0));when(sales.dispatch(9L)).thenReturn(dispatch);
        SalesLineEntity line=new SalesLineEntity();line.setId(10L);line.setPackingId(20L);line.setPackingCode("PK-QA");when(sales.line(10L)).thenReturn(line);when(sales.lineOrderId(10L)).thenReturn(11L);
        SalesOrderEntity order=new SalesOrderEntity();order.setOrganizationId(3L);when(sales.order(11L)).thenReturn(order);
        item.setItemId(30L);item.setTraceCode("PK-QA-CODE");when(items.item(20L,30L)).thenReturn(item);
        shipment.setId(40L);shipment.setStatus("DELIVERY_ISSUE");when(transport.byDispatch(9L)).thenReturn(shipment);
        receipt.setId(50L);receipt.setKind("RECEIVE");receipt.setAcceptedCount(1);receipt.setRejectedCount(1);receipt.setOccurredAt(LocalDateTime.of(2026,10,20,12,0));when(transport.receipt(40L)).thenReturn(receipt);
        when(items.receive(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyLong())).thenReturn(1);
    }
    @AfterEach void clear(){AccessContext.clear();}
    private ItemCirculationRequests.Link link(){ItemCirculationRequests.Link r=new ItemCirculationRequests.Link();r.setItemIds(List.of(30L));r.setRequestKey("00000000-0000-4000-8000-000000000001");return r;}
    private ItemCirculationRequests.Receipt receive(){ItemCirculationRequests.Receipt r=new ItemCirculationRequests.Receipt();r.setItemIds(List.of(30L));r.setOutcome("ACCEPTED");r.setRequestKey("00000000-0000-4000-8000-000000000002");return r;}
    @Test void linkLocksPackagingBeforeReadingAllocationsAndDoesNotWriteSalesOrStock(){service.link(9L,link());org.mockito.InOrder sequence=inOrder(packing,items);sequence.verify(packing).lock(20L);sequence.verify(items).operation(anyString());sequence.verify(items).linkedCount(9L);verify(items).link(30L,9L,link().getRequestKey(),1L);verify(sales,never()).insertDispatch(any());verify(sales,never()).status(any(),any());}
    @Test void dispatchCapacityCannotBeExceeded(){when(items.linkedCount(9L)).thenReturn(2L);assertThrows(IllegalArgumentException.class,()->service.link(9L,link()));verify(items,never()).insertOperation(any());}
    @Test void foreignSourceCannotBeAssigned(){when(items.item(20L,30L)).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.link(9L,link()));verify(items,never()).link(any(),any(),any(),any());}
    @Test void codeAlreadyAssignedToSameOrAnotherDispatchIsRejected(){item.setDispatchId(9L);assertThrows(IllegalArgumentException.class,()->service.link(9L,link()));item.setDispatchId(99L);assertThrows(IllegalArgumentException.class,()->service.link(9L,link()));}
    @Test void duplicateAndEmptySelectionsAreRejected(){ItemCirculationRequests.Link r=link();r.setItemIds(List.of(30L,30L));assertThrows(IllegalArgumentException.class,()->service.link(9L,r));r.setItemIds(List.of());assertThrows(IllegalArgumentException.class,()->service.link(9L,r));}
    @Test void identicalOperationReplaysButChangedActionOrSelectionDoesNot(){
        doAnswer(c->{Operation op=c.getArgument(0);when(items.operation(op.getRequestKey())).thenReturn(op);return 1;}).when(items).insertOperation(any());
        service.link(9L,link());service.link(9L,link());verify(items,times(1)).link(any(),any(),any(),any());
        ItemCirculationRequests.Link altered=link();altered.setItemIds(List.of(31L));assertThrows(IllegalArgumentException.class,()->service.link(9L,altered));
        ItemCirculationRequests.Receipt other=receive();other.setRequestKey(link().getRequestKey());assertThrows(IllegalArgumentException.class,()->service.receive(9L,other));
    }
    @Test void explicitReceiptIsBoundToOriginalAggregateAndCanReplay(){
        item.setDispatchId(9L);doAnswer(c->{Operation op=c.getArgument(0);when(items.operation(op.getRequestKey())).thenReturn(op);return 1;}).when(items).insertOperation(any());
        service.receive(9L,receive());service.receive(9L,receive());verify(items,times(1)).receive(30L,9L,50L,"ACCEPTED",receive().getRequestKey(),1L);verify(transport).lock(40L);
        ItemCirculationRequests.Receipt altered=receive();altered.setOutcome("REJECTED");assertThrows(IllegalArgumentException.class,()->service.receive(9L,altered));
    }
    @Test void receiptRequiresShipmentAndActualAggregate(){when(transport.byDispatch(9L)).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.receive(9L,receive()));when(transport.byDispatch(9L)).thenReturn(shipment);when(transport.receipt(40L)).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.receive(9L,receive()));}
    @Test void receiptDoesNotExceedAcceptedOrRejectedQuota(){item.setDispatchId(9L);when(items.receiptCount(9L,"ACCEPTED")).thenReturn(1L);assertThrows(IllegalArgumentException.class,()->service.receive(9L,receive()));ItemCirculationRequests.Receipt r=receive();r.setOutcome("REJECTED");when(items.receiptCount(9L,"REJECTED")).thenReturn(1L);assertThrows(IllegalArgumentException.class,()->service.receive(9L,r));verify(items,never()).insertOperation(any());}
    @Test void receiptRequiresCodeOfThisDispatchAndCannotOverwritePreviousResult(){item.setDispatchId(99L);assertThrows(IllegalArgumentException.class,()->service.receive(9L,receive()));item.setDispatchId(9L);item.setReceiptOutcome("REJECTED");assertThrows(IllegalArgumentException.class,()->service.receive(9L,receive()));verify(items,never()).receive(any(),any(),any(),any(),any(),any());}
    @Test void publicUnlinkedCodeHasNoCirculationEvenWhenSiblingIsDelivered(){assertNull(service.publicView(31L));verifyNoInteractions(transport);}
    @Test void publicViewDoesNotTurnAggregateReceiptIntoIndividualAcceptanceOrLeakPersonalDetails(){
        item.setDispatchId(9L);when(items.publicLink(30L)).thenReturn(item);receipt.setLocation("PRIVATE ADDRESS");receipt.setDescription("PRIVATE NOTES");receipt.setPerformerName("PRIVATE NAME");when(transport.publicEvents(40L)).thenReturn(List.of(receipt));
        Map<String,Object> result=service.publicView(30L);assertEquals("NOT_SUBMITTED",result.get("chainStatus"));assertNull(result.get("receiptOutcome"));assertEquals(List.of(),result.get("events"));assertFalse(result.toString().contains("PRIVATE"));
        item.setReceiptOutcome("REJECTED");result=service.publicView(30L);assertEquals("REJECTED",result.get("receiptOutcome"));assertEquals(List.of(Map.of("kind","RECEIVE","occurredAt",receipt.getOccurredAt())),result.get("events"));
    }
    @Test void publicHistoryUsesLatestTwoHundredAndMarksTruncation(){item.setDispatchId(9L);when(items.publicLink(30L)).thenReturn(item);Event event=new Event();event.setKind("UPDATE");event.setOccurredAt(LocalDateTime.now());when(transport.publicEvents(40L)).thenReturn(Collections.nCopies(201,event));Map<String,Object> result=service.publicView(30L);assertEquals(true,result.get("eventsTruncated"));assertEquals(200,((List<?>)result.get("events")).size());}
    @Test void writesAndReadsRespectRolesAndOrganizations(){actor.setOrganizationId(8L);assertThrows(AccessException.class,()->service.detail(9L,"","AVAILABLE",1));assertThrows(AccessException.class,()->service.link(9L,link()));assertThrows(AccessException.class,()->service.receive(9L,receive()));actor.setOrganizationId(3L);actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.link(9L,link()));assertThrows(AccessException.class,()->service.receive(9L,receive()));}
    @Test void invalidPaginationModesAndOversizedSelectionsReject(){assertThrows(IllegalArgumentException.class,()->service.detail(9L,"","BAD",1));assertThrows(IllegalArgumentException.class,()->service.detail(9L,"","AVAILABLE",0));ItemCirculationRequests.Link r=link();r.setItemIds(Collections.nCopies(101,30L));assertThrows(IllegalArgumentException.class,()->service.link(9L,r));}
}
