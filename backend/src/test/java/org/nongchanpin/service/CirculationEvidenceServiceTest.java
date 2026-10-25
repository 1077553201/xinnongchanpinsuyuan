package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.chain.*;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.dto.SubmitCirculationEvidenceRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.transaction.*;

class CirculationEvidenceServiceTest {
    private final CirculationEvidenceMapper evidence=mock(CirculationEvidenceMapper.class);
    private final PackingTraceMapper packing=mock(PackingTraceMapper.class);
    private final TraceItemMapper items=mock(TraceItemMapper.class);
    private final TraceEventMapper events=mock(TraceEventMapper.class);
    private final PackingEvidenceMapper production=mock(PackingEvidenceMapper.class);
    private final ProductBatchMapper batches=mock(ProductBatchMapper.class);
    private final ItemCirculationService circulation=mock(ItemCirculationService.class);
    private final PayloadService payloads=new PayloadService();
    private final ChainService chain=mock(ChainService.class);
    private final ChainTransactionService transactions=mock(ChainTransactionService.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final CirculationEvidenceService service=new CirculationEvidenceService(evidence,packing,items,events,production,batches,circulation,payloads,chain,transactions,mock(AuditService.class),new ContractConfig(),manager);
    private final Map<Long,CirculationEvidenceEntity> versions=new LinkedHashMap<>();
    private final Map<Long,TraceEventEntity> local=new LinkedHashMap<>();
    private final Map<Long,ChainTransactionEntity> txs=new LinkedHashMap<>();
    private final List<ChainTraceEventView> live=new ArrayList<>();
    private final Map<String,Object> facts=new LinkedHashMap<>();
    private final Actor actor=new Actor();
    private final TraceItemEntity item=new TraceItemEntity();
    private final ChainBatchView chainBatch=new ChainBatchView();
    private int chainItemStatus=1;
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(evidence.lockOrderForItem(40L)).thenReturn(11L);
        Source source=new Source();source.setOrganizationId(3L);when(packing.source(9L)).thenReturn(source);
        Bridge bridge=new Bridge();bridge.setPackingId(9L);bridge.setBatchId(20L);bridge.setSourceJson("{\"packingCode\":\"PACK-QA\"}");bridge.setSourceHash(payloads.payloadHash(bridge.getSourceJson()));when(packing.publicBridge(40L)).thenReturn(bridge);
        PackingEvidenceModels.Snapshot saved=new PackingEvidenceModels.Snapshot();saved.setManifestJson(payloads.canonicalJson(Map.of("source",Map.of("sourceHash",bridge.getSourceHash()))));saved.setManifestHash(payloads.payloadHash(saved.getManifestJson()));saved.setBatchPayload(payloads.canonicalJson(Map.of("batchCode","PACK-QA","productCode","PRODUCT-QA","metadata",Map.of("sourceHash",bridge.getSourceHash(),"evidenceHash",saved.getManifestHash()))));when(production.snapshot(9L)).thenReturn(saved);
        item.setId(40L);item.setBatchId(20L);item.setTraceCodePlain("CODE-QA");item.setTraceCodeHash(HashUtils.hashBusinessIdPlainHex("CODE-QA"));item.setTraceUrl("https://example.test/t/CODE-QA");item.setChainStatus("CONFIRMED");item.setStatus("ACTIVE");when(items.findById(40L)).thenReturn(item);
        ProductBatchEntity batch=new ProductBatchEntity();batch.setId(20L);batch.setBatchCode("PACK-QA");when(batches.findById(20L)).thenReturn(batch);
        chainBatch.setExists(true);chainBatch.setStatus(BigInteger.ONE);chainBatch.setProductIdHash(HashUtils.hashBusinessIdHex("PRODUCT-QA"));chainBatch.setPayloadHash(HashUtils.hashCanonicalTextHex(saved.getBatchPayload()));when(chain.getBatch("PACK-QA")).thenReturn(chainBatch);
        when(chain.getItem("CODE-QA")).thenAnswer(c->{ChainItemView v=new ChainItemView();v.setExists(true);v.setStatus(BigInteger.valueOf(chainItemStatus));v.setBatchIdHash(HashUtils.hashBusinessIdHex("PACK-QA"));v.setPayloadHash(payloads.payloadHashWithPrefix(Map.of("traceCodeHash",item.getTraceCodeHash(),"batchCode","PACK-QA","traceUrl",item.getTraceUrl())));v.setTraceEventCount(BigInteger.valueOf(live.size()));return v;});
        when(chain.getTraceEvent(eq("CODE-QA"),anyLong())).thenAnswer(c->live.get(((Long)c.getArgument(1)).intValue()));
        facts.put("dispatchedAt","2026-10-20T10:00:00");facts.put("linkedAt","2026-10-20T11:00:00");facts.put("chainStatus","NOT_SUBMITTED");facts.put("transportStatus","NOT_ARRANGED");facts.put("events",List.of());facts.put("eventsTruncated",false);when(circulation.publicView(40L)).thenAnswer(c->new LinkedHashMap<>(facts));
        when(evidence.latest(40L)).thenAnswer(c->versions.values().stream().reduce((a,b)->b).orElse(null));when(evidence.find(anyLong())).thenAnswer(c->versions.get(c.getArgument(0)));when(evidence.versions(40L)).thenAnswer(c->{List<CirculationEvidenceEntity> rows=new ArrayList<>(versions.values());Collections.reverse(rows);return rows;});when(evidence.byVersion(eq(40L),anyInt())).thenAnswer(c->versions.values().stream().filter(v->v.getVersion().equals(c.getArgument(1))).findFirst().orElse(null));
        doAnswer(c->{CirculationEvidenceEntity r=c.getArgument(0);r.setId((long)versions.size()+1);r.setCreatedAt(LocalDateTime.now());versions.put(r.getId(),r);return 1;}).when(evidence).insert(any());
        doAnswer(c->{CirculationEvidenceEntity r=versions.get(c.getArgument(0));r.setLeaseToken(c.getArgument(1));r.setLeaseUntil(c.getArgument(2));return 1;}).when(evidence).claim(any(),any(),any());doAnswer(c->{CirculationEvidenceEntity r=versions.get(c.getArgument(0));if(Objects.equals(r.getLeaseToken(),c.getArgument(1))){r.setLeaseToken(null);r.setLeaseUntil(null);}return 1;}).when(evidence).release(any(),any());
        // MySQL JSON columns return normalized spacing, not the original canonical text.
        doAnswer(c->{TraceEventEntity e=c.getArgument(0);e.setId((long)local.size()+100);e.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payloads.readJson(e.getPayload())));local.put(e.getId(),e);return 1;}).when(events).insert(any());when(events.findById(any())).thenAnswer(c->local.get(c.getArgument(0)));
        doAnswer(c->{local.get(c.getArgument(0)).setChainStatus(c.getArgument(1));return 1;}).when(events).updateChainStatus(any(),any());
        when(transactions.createPending(any(),any(),any(),any(),any(),any(),any())).thenAnswer(c->{ChainTransactionEntity t=new ChainTransactionEntity();t.setId((long)txs.size()+1);t.setChainStatus("PENDING");txs.put(t.getId(),t);return t;});when(transactions.get(any())).thenAnswer(c->txs.get(c.getArgument(0)));when(transactions.completeFromResult(any(),any())).thenAnswer(c->{ChainTransactionEntity t=c.getArgument(0);ChainTxResult r=c.getArgument(1);t.setChainStatus(r.getChainStatus().name());t.setReceiptStatus(r.getReceiptStatus());return t;});
        when(chain.appendTraceEvent(any())).thenAnswer(c->{append(c.getArgument(0));return confirmed();});
    }
    @AfterEach void clear(){AccessContext.clear();}
    @BeforeEach void lockedRecord(){when(evidence.lockFrozen(anyLong())).thenAnswer(c->evidence.find(c.getArgument(0)));}
    @Test void reconciliationConfirmsExistingFlowWithoutResending() {
        service.submit(40L,request());CirculationEvidenceEntity row=versions.values().iterator().next();row.setChainStatus("UNCERTAIN");
        clearInvocations(chain,transactions);service.reconcileFrozen(row.getId(),row.getManifestHash(),row.getTxId());
        assertEquals("CONFIRMED",row.getChainStatus());verify(chain,never()).appendTraceEvent(any());verify(transactions,never()).createPending(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void reconciliationCannotSendMissingFlowOrReplaceIntent() {
        service.submit(40L,request());CirculationEvidenceEntity row=versions.values().iterator().next();live.clear();clearInvocations(chain,transactions);
        service.reconcileFrozen(row.getId(),row.getManifestHash(),row.getTxId());
        assertEquals("UNCERTAIN",row.getChainStatus());verify(chain,never()).appendTraceEvent(any());verify(transactions,never()).createPending(any(),any(),any(),any(),any(),any(),any());
        assertThrows(IllegalArgumentException.class,()->service.reconcileFrozen(row.getId(),row.getManifestHash(),999L));
    }
    private SubmitCirculationEvidenceRequest request(){SubmitCirculationEvidenceRequest r=new SubmitCirculationEvidenceRequest();r.setExpectedManifestHash((String)service.preview(40L).get("manifestHash"));return r;}
    private ChainTxResult confirmed(){ChainTxResult r=new ChainTxResult();r.setChainStatus(ChainStatus.CONFIRMED);r.setReceiptStatus("0");return r;}
    private void append(AppendTraceEventCommand c){ChainTraceEventView v=new ChainTraceEventView();v.setIndex(BigInteger.valueOf(live.size()));v.setItemCodeHash(HashUtils.hashBusinessIdHex(c.getItemCode()));v.setEventIdHash(HashUtils.hashBusinessIdHex(c.getEventId()));v.setEventType(BigInteger.valueOf(c.getEventType()));v.setOccurredAt(BigInteger.valueOf(c.getOccurredAt()));v.setPayloadHash(payloads.payloadHashWithPrefix(c.getPayload()));live.add(v);}
    @Test void previewDoesNotFreezeOrWriteChain(){service.preview(40L);assertTrue(versions.isEmpty());verify(events,never()).insert(any());verify(chain,never()).appendTraceEvent(any());}
    @Test void freezesAfterLockingOrderPackagingAndShipmentInBusinessOrder(){when(evidence.shipmentForItem(40L)).thenReturn(50L);service.submit(40L,request());org.mockito.InOrder order=inOrder(evidence,packing);order.verify(evidence).lockOrderForItem(40L);order.verify(packing).lock(9L);order.verify(evidence).shipmentForItem(40L);order.verify(evidence).lockShipment(50L);}
    @Test void intentAndSnapshotPersistBeforeExternalWrite(){doAnswer(c->{assertEquals(1,versions.size());assertNotNull(versions.get(1L).getTxId());assertEquals("PENDING",local.get(100L).getChainStatus());append(c.getArgument(0));return confirmed();}).when(chain).appendTraceEvent(any());service.submit(40L,request());assertEquals("CONFIRMED",versions.get(1L).getChainStatus());assertNull(versions.get(1L).getLeaseToken());}
    @Test void repeatSubmissionReadsExistingEventAndDoesNotAppendAgain(){SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);service.submit(40L,r);assertEquals(1,versions.size());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void staleConfirmationRejectsBeforeFreeze(){SubmitCirculationEvidenceRequest r=request();facts.put("transportStatus","PLANNED");assertThrows(IllegalArgumentException.class,()->service.submit(40L,r));assertTrue(versions.isEmpty());}
    @Test void writeRolesOrganizationAndProductionReadinessAreChecked(){SubmitCirculationEvidenceRequest r=request();actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.submit(40L,r));actor.setRole("MANAGER");actor.setOrganizationId(8L);assertThrows(AccessException.class,()->service.preview(40L));actor.setOrganizationId(3L);item.setChainStatus("NOT_SUBMITTED");assertThrows(IllegalArgumentException.class,()->service.submit(40L,r));verify(chain,never()).appendTraceEvent(any());}
    @Test void unknownTransactionIsNotBlindlyRetried(){doThrow(new IllegalStateException("timeout")).when(chain).appendTraceEvent(any());SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);service.submit(40L,r);assertEquals("UNCERTAIN",versions.get(1L).getChainStatus());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void unknownVersionCanRecoverEvenWhenBusinessHasNewFacts(){
        doThrow(new IllegalStateException("timeout")).when(chain).appendTraceEvent(any());SubmitCirculationEvidenceRequest original=request();service.submit(40L,original);facts.put("transportStatus","PLANNED");assertThrows(IllegalArgumentException.class,()->service.submit(40L,request()));
        AppendTraceEventCommand c=new AppendTraceEventCommand();TraceEventEntity e=local.get(100L);c.setItemCode("CODE-QA");c.setEventId("TRACE_EVENT:100");c.setEventType(8);c.setOccurredAt(e.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());c.setPayload(payloads.readJson(e.getPayload()));append(c);
        service.submit(40L,original);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());assertFalse((Boolean)service.preview(40L).get("frozen"));verify(chain,times(1)).appendTraceEvent(any());
    }
    @Test void sourceMismatchDoesNotAppend(){chainBatch.setPayloadHash("0xwrong");service.submit(40L,request());assertEquals("SOURCE_MISMATCH",versions.get(1L).getErrorCode());verify(chain,never()).appendTraceEvent(any());}
    @Test void inactiveChainSourceStopsPublication(){chainBatch.setStatus(BigInteger.valueOf(3));service.submit(40L,request());assertEquals("SOURCE_NOT_ACTIVE",versions.get(1L).getErrorCode());verify(chain,never()).appendTraceEvent(any());}
    @Test void recalledItemBlocksNewWritesButDoesNotEraseExistingProof(){SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);chainItemStatus=4;service.submit(40L,r);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());facts.put("transportStatus","PLANNED");service.submit(40L,request());assertEquals("SOURCE_NOT_ACTIVE",versions.get(2L).getErrorCode());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void conflictingExistingEventIsNeverOverwritten(){doAnswer(c->{append(c.getArgument(0));live.get(0).setPayloadHash("0xwrong");throw new IllegalStateException("timeout");}).when(chain).appendTraceEvent(any());SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);service.submit(40L,r);assertEquals("HASH_MISMATCH",versions.get(1L).getErrorCode());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void chainDisabledMayBeExplicitlyRetried(){doReturn(ChainTxResult.disabled("0xcontract","disabled")).when(chain).appendTraceEvent(any());SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);assertEquals("CHAIN_DISABLED",versions.get(1L).getChainStatus());doAnswer(c->{append(c.getArgument(0));return confirmed();}).when(chain).appendTraceEvent(any());service.submit(40L,r);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());}
    @Test void definiteFailedReceiptCanRetry(){ChainTxResult failure=new ChainTxResult();failure.setChainStatus(ChainStatus.FAILED);failure.setReceiptStatus("16");doReturn(failure).when(chain).appendTraceEvent(any());SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);doAnswer(c->{append(c.getArgument(0));return confirmed();}).when(chain).appendTraceEvent(any());service.submit(40L,r);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());}
    @Test void failureWithoutReceiptIsUnknownAndNotRetried(){ChainTxResult failure=new ChainTxResult();failure.setChainStatus(ChainStatus.FAILED);doReturn(failure).when(chain).appendTraceEvent(any());SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);service.submit(40L,r);assertEquals("UNCERTAIN",versions.get(1L).getChainStatus());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void unavailableReadDoesNotSend(){when(chain.getBatch(any())).thenThrow(new IllegalStateException("offline"));service.submit(40L,request());assertEquals("NOT_SUBMITTED",versions.get(1L).getChainStatus());verify(chain,never()).appendTraceEvent(any());}
    @Test void leaseBlocksConcurrentSubmission(){SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);versions.get(1L).setLeaseUntil(LocalDateTime.now().plusMinutes(1));assertThrows(IllegalArgumentException.class,()->service.submit(40L,r));verify(chain,times(1)).appendTraceEvent(any());}
    @Test void newVersionPreservesOldSnapshotAndLinksItsDigest(){service.submit(40L,request());String json=versions.get(1L).getManifestJson(),hash=versions.get(1L).getManifestHash();facts.put("transportStatus","PLANNED");service.submit(40L,request());assertEquals(2,versions.size());assertEquals(json,versions.get(1L).getManifestJson());assertEquals(hash,versions.get(2L).getPreviousHash());assertEquals("CONFIRMED",versions.get(2L).getChainStatus());}
    @Test void versionLimitKeepsRecoveryAvailableWithoutAbsorbingNewFacts(){for(int i=1;i<=50;i++){facts.put("events",List.of(Map.of("kind","UPDATE","occurredAt","2026-10-25T10:"+String.format("%02d",i)+":00")));service.submit(40L,request());}facts.put("events",List.of(Map.of("kind","UPDATE","occurredAt","2026-10-25T11:00:00")));Map<String,Object> preview=service.preview(40L);assertEquals(true,preview.get("versionLimitReached"));service.submit(40L,request());assertEquals(50,versions.size());verify(chain,times(50)).appendTraceEvent(any());}
    @Test void missingLiveParentPreventsSkippingHistory(){service.submit(40L,request());live.clear();facts.put("transportStatus","PLANNED");service.submit(40L,request());assertEquals("PREVIOUS_NOT_VERIFIED",versions.get(2L).getErrorCode());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void damagedFrozenEvidenceCannotBePresentedOrResubmitted(){SubmitCirculationEvidenceRequest r=request();service.submit(40L,r);versions.get(1L).setManifestJson("{}");assertThrows(IllegalArgumentException.class,()->service.preview(40L));assertThrows(IllegalArgumentException.class,()->service.submit(40L,r));}
    @Test void publicVerificationDoesNotExtendOldProofToNewFacts(){
        service.submit(40L,request());PublicTraceView.ChainVerification v=new PublicTraceView.ChainVerification();v.setVerificationSource("LIVE_CHAIN");v.setBatchPayloadHashMatched(true);v.setItemPayloadHashMatched(true);List<PublicTraceView.Record> records=new ArrayList<>();for(int i=0;i<3;i++){PublicTraceView.Record r=new PublicTraceView.Record();r.setVerificationSource("LIVE_CHAIN");r.setPayloadHashMatched(true);records.add(r);}v.setRecords(records);
        Map<String,Object> result=service.publicView(40L,new ArrayList<>(local.values()),v);assertEquals("VERIFIED",result.get("verificationStatus"));facts.put("transportStatus","PLANNED");result=service.publicView(40L,new ArrayList<>(local.values()),v);assertEquals("NOT_VERIFIED",result.get("verificationStatus"));assertEquals(false,result.get("currentMatchesLatest"));v.setVerificationSource("UNAVAILABLE");result=service.publicView(40L,new ArrayList<>(local.values()),v);assertEquals("NOT_VERIFIED",((Map<?,?>)((List<?>)result.get("versions")).get(0)).get("verificationStatus"));
    }
    @Test void publicLatestVersionRequiresItsPredecessorToBeLiveVerified(){
        service.submit(40L,request());facts.put("transportStatus","PLANNED");service.submit(40L,request());
        PublicTraceView.ChainVerification v=new PublicTraceView.ChainVerification();v.setVerificationSource("LIVE_CHAIN");v.setBatchPayloadHashMatched(true);v.setItemPayloadHashMatched(true);List<PublicTraceView.Record> records=new ArrayList<>();for(int i=0;i<4;i++){PublicTraceView.Record r=new PublicTraceView.Record();r.setVerificationSource("LIVE_CHAIN");r.setPayloadHashMatched(i!=2);records.add(r);}v.setRecords(records);
        Map<String,Object> result=service.publicView(40L,new ArrayList<>(local.values()),v);assertEquals("NOT_VERIFIED",result.get("verificationStatus"));records.get(2).setPayloadHashMatched(true);assertEquals("VERIFIED",service.publicView(40L,new ArrayList<>(local.values()),v).get("verificationStatus"));
    }
}
