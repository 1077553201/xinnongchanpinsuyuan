package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.LossCorrectionRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;

class LossCorrectionServiceTest {
    private final InventoryMapper inventory=mock(InventoryMapper.class);
    private final LossCorrectionMapper corrections=mock(LossCorrectionMapper.class);
    private final LossCorrectionService service=new LossCorrectionService(inventory,corrections,new PayloadService(),mock(AuditService.class));
    private final InventoryMovementEntity original=new InventoryMovementEntity();
    private final InventorySource source=new InventorySource();
    private final Actor actor=new Actor();
    private final List<LossCorrectionEntity> history=new ArrayList<>();
    private int balance=5;
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        source.setId(5L);source.setOrganizationId(3L);source.setPackageCount(10);source.setReceivedCount(10);source.setAvailableCount(5);source.setPackageUnit("盒");source.setNetWeightKg(new BigDecimal("1.000"));when(inventory.source(5L)).thenReturn(source);
        original.setId(7L);original.setPackingId(5L);original.setWarehouseId(9L);original.setMovementCode("QA-LOSS");original.setKind("LOSS");original.setPackageCount(3);original.setPackageUnit("盒");original.setWeightKg(new BigDecimal("3.000"));original.setOccurredAt(LocalDateTime.now().minusDays(1).withNano(0));when(inventory.movement(7L)).thenReturn(original);
        WarehouseEntity warehouse=new WarehouseEntity();warehouse.setOrganizationId(3L);when(inventory.warehouse(9L)).thenReturn(warehouse);
        when(inventory.balance(5L,9L)).thenAnswer(c->balance);when(corrections.history(7L)).thenAnswer(c->new ArrayList<>(history));
        when(corrections.orders(5L)).thenReturn(List.of());when(corrections.codes(5L)).thenReturn(List.of());when(corrections.evidence(5L)).thenReturn(List.of());when(inventory.positions(5L)).thenReturn(List.of());
        doAnswer(c->{LossCorrectionEntity row=c.getArgument(0);row.setId((long)history.size()+1);history.add(row);balance=row.getBalanceAfter().intValue();source.setAvailableCount(balance);when(corrections.byKey(row.getRequestKey())).thenReturn(row);return 1;}).when(corrections).insert(any());
    }
    @AfterEach void clear(){AccessContext.clear();}
    private LossCorrectionRequest request(int count){LossCorrectionRequest r=new LossCorrectionRequest();r.setCorrectedCount(count);r.setExpectedImpactHash((String)service.preview(7L,count).get("impactHash"));r.setVerifiedAt(LocalDateTime.now().minusMinutes(1).withNano(0));r.setPerformerName("核实人");r.setReason("盘核原损耗数量录错");r.setReference("QA-CORR");r.setRequestKey(UUID.randomUUID().toString());return r;}
    @Test void decreasingAndIncreasingLossAppendVersionsWithoutChangingOriginalOrInsertingStock(){service.confirm(7L,request(1));assertEquals(7,balance);assertEquals(3,original.getPackageCount());service.confirm(7L,request(4));assertEquals(4,balance);assertEquals(2,history.size());assertEquals(1,history.get(1).getPreviousCount());assertEquals(history.get(0).getCorrectionHash(),history.get(1).getPreviousHash());verify(inventory,never()).insertMovement(any());}
    @Test void zeroIsAllowedAndCountIsTotalNotDelta(){service.confirm(7L,request(0));assertEquals(8,balance);assertEquals(0,service.detail(7L).get("effectiveCount"));assertEquals(3,original.getPackageCount());}
    @Test void balanceCannotBecomeNegativeOrExceedReceivedStock(){assertThrows(IllegalArgumentException.class,()->service.preview(7L,9));source.setReceivedCount(6);assertThrows(IllegalArgumentException.class,()->service.preview(7L,0));assertTrue(history.isEmpty());}
    @Test void unchangedOrInvalidCountsDoNotCreateVersions(){assertThrows(IllegalArgumentException.class,()->service.preview(7L,3));assertThrows(IllegalArgumentException.class,()->service.preview(7L,-1));assertThrows(IllegalArgumentException.class,()->service.preview(7L,1000001));}
    @Test void identicalConfirmCanReplayAfterLaterAdjustment(){LossCorrectionRequest first=request(1);service.confirm(7L,first);service.confirm(7L,request(2));service.confirm(7L,first);assertEquals(2,history.size());first.setCorrectedCount(0);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,first));}
    @Test void changedStockAndDownstreamAssociationsRequireNewPreview(){LossCorrectionRequest r=request(1);balance=4;assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));balance=5;when(corrections.orders(5L)).thenReturn(List.of(Map.of("orderId",99L)));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));verify(corrections,never()).insert(any());}
    @Test void changedRevisionCannotUseOldConfirmation(){LossCorrectionRequest stale=request(0);service.confirm(7L,request(1));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,stale));assertEquals(1,history.size());}
    @Test void onlyLossIsSupportedNotSalesDispatchOrOrdinaryInOut(){for(String kind:List.of("IN","OUT")){original.setKind(kind);assertThrows(IllegalArgumentException.class,()->service.detail(7L));}original.setKind("LOSS");original.setSalesOrderId(11L);assertThrows(IllegalArgumentException.class,()->service.detail(7L));}
    @Test void rolesAndOrganizationsAreChecked(){actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.detail(7L));actor.setOrganizationId(3L);LossCorrectionRequest r=request(1);actor.setRole("VIEWER");service.preview(7L,1);assertThrows(AccessException.class,()->service.confirm(7L,r));}
    @Test void verificationTimeAndMissingReasonAreRejected(){LossCorrectionRequest r=request(1);r.setVerifiedAt(LocalDateTime.now().plusHours(1));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));r.setVerifiedAt(original.getOccurredAt().minusSeconds(1));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));r.setVerifiedAt(original.getOccurredAt());r.setReason(" ");assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void historyTamperingOriginalEditsAndQuantityDriftAreRejected(){service.confirm(7L,request(1));history.get(0).setReason("tampered");assertThrows(IllegalArgumentException.class,()->service.detail(7L));history.get(0).setReason("盘核原损耗数量录错");original.setReference("tampered");assertThrows(IllegalArgumentException.class,()->service.detail(7L));original.setReference(null);history.get(0).setBalanceAfter(99L);assertThrows(IllegalArgumentException.class,()->service.detail(7L));}
    @Test void previewDoesNotClaimPackageIdentityOrRewriteEvidence(){when(corrections.codes(5L)).thenReturn(List.of(Map.of("itemId",21L,"receiptOutcome","REJECTED")));when(corrections.evidence(5L)).thenReturn(List.of(Map.of("id",4L,"chainStatus","CONFIRMED")));Map<?,?> impact=(Map<?,?>)service.preview(7L,1).get("impact");assertEquals(2,impact.get("stockDelta"));assertEquals(1,((List<?>)impact.get("relatedCodes")).size());assertEquals(1,((List<?>)impact.get("evidence")).size());verify(corrections,never()).insert(any());}
    @Test void oversizedDependencySetsFailClosedInsteadOfPretendingComplete(){when(corrections.codes(5L)).thenReturn(Collections.nCopies(201,Map.of()));assertThrows(IllegalArgumentException.class,()->service.preview(7L,1));}
    @Test void packagingLockPrecedesCurrentBalanceAndHistoryRead(){service.confirm(7L,request(1));org.mockito.InOrder order=inOrder(inventory,corrections);order.verify(inventory).lockPacking(5L);order.verify(corrections).byKey(anyString());order.verify(corrections).history(7L);order.verify(inventory).balance(5L,9L);}
}
