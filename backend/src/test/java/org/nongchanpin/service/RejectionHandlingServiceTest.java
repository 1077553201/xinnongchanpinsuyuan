package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.RejectionRequests.*;
import org.nongchanpin.model.entity.RejectionModels.*;
import org.nongchanpin.model.entity.RejectionModels.Case;
import org.nongchanpin.model.entity.WarehouseEntity;
import org.nongchanpin.model.entity.PackingTraceModels;
import org.nongchanpin.security.*;

class RejectionHandlingServiceTest {
    private final RejectionMapper records=mock(RejectionMapper.class);
    private final PackingTraceMapper packing=mock(PackingTraceMapper.class);
    private final SalesMapper sales=mock(SalesMapper.class);
    private final InventoryMapper inventory=mock(InventoryMapper.class);
    private final RejectionHandlingService service=new RejectionHandlingService(records,packing,sales,inventory,new PayloadService(),mock(AuditService.class));
    private final Actor actor=new Actor();
    private final Source source=new Source();
    private final List<Event> history=new ArrayList<>();
    private Case current;
    private int sequence;
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        source.setOrganizationId(3L);source.setOrderId(2L);source.setDispatchId(4L);source.setPackingId(5L);source.setItemId(6L);
        source.setReceiptEventId(7L);source.setReceiptOutcome("REJECTED");source.setReceiptOccurredAt(LocalDateTime.now().minusDays(2).withNano(0));source.setReceiptRecordedAt(source.getReceiptOccurredAt().plusHours(1));
        source.setTraceCodeHash("a".repeat(64));source.setSourceHash("b".repeat(64));source.setPackageUnit("盒");source.setNetWeightKg(new BigDecimal("1.000"));
        when(records.source(4L,6L)).thenReturn(source);PackingTraceModels.Source original=new PackingTraceModels.Source();original.setOrganizationId(3L);when(packing.source(5L)).thenReturn(original);
        when(records.events(8L)).thenAnswer(c->new ArrayList<>(history));
        doAnswer(c->{current=c.getArgument(0);current.setId(8L);when(records.find(8L)).thenReturn(current);when(records.lock(8L)).thenAnswer(x->current);when(records.byItem(6L)).thenReturn(current);when(records.byKey(current.getRequestKey())).thenReturn(current);return 1;}).when(records).insert(any());
        doAnswer(c->{Event e=c.getArgument(0);history.add(e);when(records.eventByKey(e.getRequestKey())).thenReturn(e);return 1;}).when(records).insertEvent(any());
        WarehouseEntity warehouse=new WarehouseEntity();warehouse.setId(9L);warehouse.setOrganizationId(3L);when(inventory.warehouse(9L)).thenReturn(warehouse);
    }
    @AfterEach void clear(){AccessContext.clear();}
    private Create createRequest(){Create r=new Create();r.setDispatchId(4L);r.setItemId(6L);r.setExpectedSourceHash((String)service.preview(4L,6L).get("sourceHash"));r.setReason("PRIVATE initial reason");r.setRequestKey(UUID.randomUUID().toString());return r;}
    private void open(){service.create(createRequest());}
    private Append event(String kind){Append r=new Append();r.setKind(kind);r.setOccurredAt(source.getReceiptOccurredAt().plusHours(2+sequence++));r.setExpectedStateHash(current.getStateHash());r.setRequestKey(UUID.randomUUID().toString());r.setPerformerName("PRIVATE performer");r.setNote("PRIVATE note");r.setReference("PRIVATE reference");if("QUARANTINE_RECEIVE".equals(kind)){r.setWarehouseId(9L);r.setQuarantineLocation("PRIVATE location");}if("ANNOTATION".equals(kind))r.setTargetRevision(0);return r;}
    @Test void completeCustodyLifecycleNeverCreatesOrdinaryStockOrSalesWrites(){open();service.append(8L,event("RETURN_DISPATCH"));service.append(8L,event("QUARANTINE_RECEIVE"));assertEquals("QUARANTINED",current.getStatus());service.append(8L,event("DISPOSE"));assertEquals("DISPOSED",current.getStatus());assertEquals(3,current.getRevision());verify(inventory,times(1)).warehouse(9L);verifyNoMoreInteractions(inventory);verify(sales,times(4)).lockOrder(2L);verifyNoMoreInteractions(sales);}
    @Test void onlyExplicitRejectedCodeCanOpen(){source.setReceiptOutcome("ACCEPTED");assertThrows(IllegalArgumentException.class,()->service.preview(4L,6L));source.setReceiptOutcome(null);assertThrows(IllegalArgumentException.class,()->service.preview(4L,6L));source.setReceiptOutcome("REJECTED");source.setReceiptEventId(null);assertThrows(IllegalArgumentException.class,()->service.preview(4L,6L));verify(records,never()).insert(any());}
    @Test void creationReplaysAndRejectsDuplicateCodeAndStaleSource(){Create r=createRequest();service.create(r);service.create(r);verify(records,times(1)).insert(any());assertThrows(IllegalArgumentException.class,()->service.create(createRequest()));r.setReason("changed");assertThrows(IllegalArgumentException.class,()->service.create(r));r.setExpectedSourceHash("f".repeat(64));assertThrows(IllegalArgumentException.class,()->service.create(r));}
    @Test void duplicateEventReplaysEvenAfterNextStepButChangedContentCannot(){open();Append r=event("RETURN_DISPATCH");service.append(8L,r);service.append(8L,event("QUARANTINE_RECEIVE"));service.append(8L,r);assertEquals(2,history.size());r.setNote("changed");assertThrows(IllegalArgumentException.class,()->service.append(8L,r));}
    @Test void versionAndChronologyRejectBeforeWriting(){open();Append r=event("RETURN_DISPATCH");r.setExpectedStateHash("f".repeat(64));assertThrows(IllegalArgumentException.class,()->service.append(8L,r));r.setExpectedStateHash(current.getStateHash());r.setOccurredAt(source.getReceiptOccurredAt().minusSeconds(1));assertThrows(IllegalArgumentException.class,()->service.append(8L,r));r.setOccurredAt(LocalDateTime.now().plusHours(1));assertThrows(IllegalArgumentException.class,()->service.append(8L,r));assertTrue(history.isEmpty());}
    @Test void physicalTimestampKeepsDatabaseMicrosecondPrecision(){open();Append r=event("RETURN_DISPATCH");r.setOccurredAt(r.getOccurredAt().withNano(123456789));service.append(8L,r);assertEquals(123456000,history.get(0).getOccurredAt().getNano());service.detail(8L);}
    @Test void correctionRetainsPhysicalClockStateAndWarehouse(){open();service.append(8L,event("QUARANTINE_RECEIVE"));LocalDateTime time=current.getLastPhysicalAt();String before=current.getStateHash();Append r=event("ANNOTATION");r.setTargetRevision(1);service.append(8L,r);assertEquals("QUARANTINED",current.getStatus());assertEquals(9L,current.getWarehouseId());assertEquals(time,current.getLastPhysicalAt());assertNotEquals(before,current.getStateHash());Append disposal=event("DISPOSE");disposal.setOccurredAt(time.plusMinutes(1));service.append(8L,disposal);assertEquals("DISPOSED",current.getStatus());}
    @Test void annotationsRejectMissingFutureTargetAndWarehouseChange(){open();Append r=event("ANNOTATION");r.setTargetRevision(null);assertThrows(IllegalArgumentException.class,()->service.append(8L,r));r.setTargetRevision(1);assertThrows(IllegalArgumentException.class,()->service.append(8L,r));r.setTargetRevision(0);r.setWarehouseId(9L);assertThrows(IllegalArgumentException.class,()->service.append(8L,r));}
    @Test void crossOrganizationWarehouseAndAccessCannotWrite(){open();WarehouseEntity w=new WarehouseEntity();w.setOrganizationId(99L);when(inventory.warehouse(9L)).thenReturn(w);assertThrows(AccessException.class,()->service.append(8L,event("QUARANTINE_RECEIVE")));actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.append(8L,event("RETURN_DISPATCH")));actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.detail(8L));assertTrue(history.isEmpty());}
    @Test void illegalTransitionsCannotSkipQuarantineOrRepeatDisposal(){open();assertThrows(IllegalArgumentException.class,()->service.append(8L,event("DISPOSE")));service.append(8L,event("QUARANTINE_RECEIVE"));assertThrows(IllegalArgumentException.class,()->service.append(8L,event("RETURN_DISPATCH")));service.append(8L,event("DISPOSE"));assertThrows(IllegalArgumentException.class,()->service.append(8L,event("DISPOSE")));service.append(8L,event("ANNOTATION"));assertEquals("DISPOSED",current.getStatus());}
    @Test void publicSummaryWhitelistsDataWithoutAuthentication(){assertNull(service.publicView(6L));open();service.append(8L,event("QUARANTINE_RECEIVE"));AccessContext.clear();Map<String,Object> result=service.publicView(6L);assertEquals(Set.of("status","revision","handlingHash","events"),result.keySet());assertFalse(result.toString().contains("PRIVATE"));assertFalse(result.containsKey("warehouseId"));}
    @Test void changedHistorySourceAndDerivedStateAreDetected(){open();service.append(8L,event("RETURN_DISPATCH"));history.get(0).setNote("tampered");assertThrows(IllegalArgumentException.class,()->service.detail(8L));history.get(0).setNote("PRIVATE note");source.setReceiptOutcome("ACCEPTED");assertThrows(IllegalArgumentException.class,()->service.detail(8L));source.setReceiptOutcome("REJECTED");current.setStatus("DISPOSED");assertThrows(IllegalArgumentException.class,()->service.detail(8L));}
    @Test void listValidatesFiltersAndSeparatesQuarantineTotals(){when(records.quarantineUnits(3L,null)).thenReturn(List.of());when(records.count(3L,null,"DISPOSED","CODE")).thenReturn(0L);service.list(null,"DISPOSED","CODE",1);verify(records).quarantineUnits(3L,null);assertThrows(IllegalArgumentException.class,()->service.list(null,"BAD","",1));assertThrows(IllegalArgumentException.class,()->service.list(null,"ALL","",0));}
}
