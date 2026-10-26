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

class MovementCorrectionServiceTest {
    private final InventoryMapper inventory=mock(InventoryMapper.class);
    private final LossCorrectionMapper dependencies=mock(LossCorrectionMapper.class);
    private final MovementCorrectionMapper corrections=mock(MovementCorrectionMapper.class);
    private final MovementCorrectionService service=new MovementCorrectionService(inventory,dependencies,new PayloadService(),mock(AuditService.class),corrections);
    private final Actor actor=new Actor();private final InventorySource source=new InventorySource();private final InventoryMovementEntity original=new InventoryMovementEntity();
    private final List<LossCorrectionEntity> history=new ArrayList<>();private int balance=5;
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        source.setId(5L);source.setOrganizationId(3L);source.setPackageCount(10);source.setReceivedCount(5);source.setAvailableCount(5);source.setPackageUnit("盒");source.setNetWeightKg(BigDecimal.ONE);when(inventory.source(5L)).thenReturn(source);
        original.setId(7L);original.setPackingId(5L);original.setWarehouseId(9L);original.setKind("IN");original.setPackageCount(5);original.setWeightKg(new BigDecimal("5.000"));original.setPackageUnit("盒");original.setOccurredAt(LocalDateTime.now().minusDays(1).withNano(0));when(inventory.movement(7L)).thenReturn(original);
        WarehouseEntity warehouse=new WarehouseEntity();warehouse.setOrganizationId(3L);when(inventory.warehouse(9L)).thenReturn(warehouse);when(inventory.balance(5L,9L)).thenAnswer(c->balance);when(corrections.latestMovement(5L,9L)).thenReturn(7L);
        when(corrections.history(7L)).thenAnswer(c->new ArrayList<>(history));when(dependencies.orders(5L)).thenReturn(List.of());when(dependencies.codes(5L)).thenReturn(List.of());when(dependencies.evidence(5L)).thenReturn(List.of());when(inventory.positions(5L)).thenReturn(List.of());
        doAnswer(c->{LossCorrectionEntity row=c.getArgument(0);row.setId((long)history.size()+1);history.add(row);int delta=row.getBalanceAfter().intValue()-balance;balance+=delta;source.setAvailableCount(source.getAvailableCount()+delta);if("IN".equals(original.getKind()))source.setReceivedCount(source.getReceivedCount()+delta);when(corrections.byKey(row.getRequestKey())).thenReturn(row);return 1;}).when(corrections).insert(any());
    }
    @AfterEach void clear(){AccessContext.clear();}
    private LossCorrectionRequest request(int count){LossCorrectionRequest r=new LossCorrectionRequest();r.setCorrectedCount(count);r.setExpectedImpactHash((String)service.preview(7L,count).get("impactHash"));r.setVerifiedAt(LocalDateTime.now().minusMinutes(1).withNano(0));r.setPerformerName("QA");r.setReason("核实原普通库存单数量录错");r.setRequestKey(UUID.randomUUID().toString());return r;}
    private void out(){original.setKind("OUT");original.setPackageCount(2);original.setWeightKg(new BigDecimal("2.000"));source.setReceivedCount(7);}
    @Test void inboundCorrectionChangesReceivedAndAvailableWithoutChangingOriginal(){service.confirm(7L,request(3));assertEquals(3,balance);assertEquals(3,source.getReceivedCount());assertEquals(5,original.getPackageCount());service.confirm(7L,request(0));assertEquals(0,balance);service.confirm(7L,request(4));assertEquals(4,balance);assertEquals(3,history.size());verify(inventory,never()).insertMovement(any());verify(dependencies,never()).insert(any());}
    @Test void ordinaryOutboundCorrectionUsesOppositeSignAndRetainsReceived(){out();service.confirm(7L,request(1));assertEquals(6,balance);assertEquals(7,source.getReceivedCount());service.confirm(7L,request(3));assertEquals(4,balance);assertEquals(2,original.getPackageCount());}
    @Test void inboundCannotExceedActualPackagingIncludingOtherWarehouses(){source.setReceivedCount(10);source.setAvailableCount(10);assertThrows(IllegalArgumentException.class,()->service.preview(7L,6));}
    @Test void balancesCannotBecomeNegativeOrExceedEffectiveReceipts(){out();assertThrows(IllegalArgumentException.class,()->service.preview(7L,8));source.setAvailableCount(7);assertThrows(IllegalArgumentException.class,()->service.preview(7L,0));}
    @Test void newerWarehouseMovementBlocksPreviewAndDirectConfirm(){LossCorrectionRequest r=request(3);when(corrections.latestMovement(5L,9L)).thenReturn(8L);Map<String,Object> preview=service.preview(7L,3);assertEquals(false,preview.get("canConfirm"));assertTrue(preview.toString().contains("后续流水"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void salesDispatchAndAnyHistoricalOrderBlockOrdinaryCorrection(){out();LossCorrectionRequest r=request(1);when(corrections.salesDispatchCount(7L)).thenReturn(1L);when(corrections.orderLineCount(5L)).thenReturn(1L);assertEquals(false,service.preview(7L,1).get("canConfirm"));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));assertTrue(history.isEmpty());}
    @Test void linkedIndividualCodesBlockEvenWhenOrderSummaryMissing(){when(corrections.linkedCodeCount(5L)).thenReturn(1L);assertEquals(false,service.preview(7L,3).get("canConfirm"));}
    @Test void frozenProductionEvidenceIsListedAndNeverOverwritten(){when(dependencies.evidence(5L)).thenReturn(List.of(Map.of("kind","PRODUCTION","id",44L,"chainStatus","CONFIRMED")));assertEquals(true,service.preview(7L,3).get("canConfirm"));service.confirm(7L,request(3));verify(dependencies,never()).insert(any());verify(inventory,never()).insertMovement(any());}
    @Test void repeatRequestReplaysAfterLaterVersionAndRejectsChangedContent(){LossCorrectionRequest r=request(3);service.confirm(7L,r);service.confirm(7L,request(4));service.confirm(7L,r);assertEquals(2,history.size());r.setReason("changed");assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));}
    @Test void previewBecomesStaleWhenBalanceOrNewVersionChanges(){LossCorrectionRequest r=request(3);balance=4;source.setAvailableCount(4);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));balance=5;source.setAvailableCount(5);service.confirm(7L,request(4));assertThrows(IllegalArgumentException.class,()->service.confirm(7L,r));}
    @Test void readonlyAndOtherOrganizationsCannotWriteOrReadPrivateRecords(){LossCorrectionRequest r=request(3);actor.setRole("VIEWER");service.detail(7L);assertThrows(AccessException.class,()->service.confirm(7L,r));actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.detail(7L));}
    @Test void lossCannotUseOrdinaryStorageAndEndpoints(){original.setKind("LOSS");assertThrows(IllegalArgumentException.class,()->service.detail(7L));verify(corrections,never()).history(any());}
    @Test void priorVersionsAndOriginalFactChangesAreDetected(){service.confirm(7L,request(3));history.get(0).setCorrectedCount(4);assertThrows(IllegalArgumentException.class,()->service.detail(7L));history.get(0).setCorrectedCount(3);original.setReference("changed");assertThrows(IllegalArgumentException.class,()->service.detail(7L));}
    @Test void zeroAndNoChangeDoNotCreatePhantomMovements(){service.confirm(7L,request(0));assertEquals(0,service.detail(7L).get("effectiveCount"));assertThrows(IllegalArgumentException.class,()->service.preview(7L,0));verify(inventory,never()).insertMovement(any());}
    @Test void successfulRequestCanReplayAfterNewDownstreamButNewAdjustmentCannot(){LossCorrectionRequest r=request(3);service.confirm(7L,r);when(corrections.latestMovement(5L,9L)).thenReturn(8L);when(corrections.orderLineCount(5L)).thenReturn(1L);service.confirm(7L,r);assertEquals(1,history.size());LossCorrectionRequest fresh=request(4);assertThrows(IllegalArgumentException.class,()->service.confirm(7L,fresh));assertEquals(1,history.size());}
}
