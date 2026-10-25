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
import org.nongchanpin.model.dto.QualityEvidenceRequests.Submit;
import org.nongchanpin.model.entity.QualityEvidenceModels.Evidence;
import org.nongchanpin.model.entity.SourceQualityModels.Report;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.transaction.*;

class QualityEvidenceServiceTest {
    private final QualityEvidenceMapper evidence=mock(QualityEvidenceMapper.class);
    private final PackingTraceMapper packing=mock(PackingTraceMapper.class);
    private final TraceItemMapper items=mock(TraceItemMapper.class);
    private final TraceEventMapper events=mock(TraceEventMapper.class);
    private final PackingEvidenceMapper production=mock(PackingEvidenceMapper.class);
    private final ProductBatchMapper batches=mock(ProductBatchMapper.class);
    private final SourceQualityMapper reports=mock(SourceQualityMapper.class);
    private final SourceQualityService quality=mock(SourceQualityService.class);
    private final Report report=new Report();
    private final PayloadService payloads=new PayloadService();
    private final ChainService chain=mock(ChainService.class);
    private final ChainTransactionService transactions=mock(ChainTransactionService.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final QualityEvidenceService service=new QualityEvidenceService(evidence,reports,quality,packing,items,events,production,batches,payloads,chain,transactions,mock(AuditService.class),new ContractConfig(),manager);
    private final Map<Long,Evidence> versions=new LinkedHashMap<>();
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
        report.setId(5L);report.setOrganizationId(3L);report.setHarvestId(17L);report.setStatus("PUBLISHED");report.setPublishedAt(LocalDateTime.of(2026,10,20,10,0));report.setManifestJson(payloads.canonicalJson(Map.of("scopeType","PACKING","packingId",9,"fileSha256","a".repeat(64),"declaredResult","PASS")));report.setManifestHash(payloads.payloadHash(report.getManifestJson()));
        when(quality.checkedReport(5L)).thenAnswer(c->{AccessContext.checkOrganization(3L);return report;});when(reports.report(5L)).thenReturn(report);when(reports.lock(5L)).thenReturn(report);
        Source source=new Source();source.setOrganizationId(3L);when(packing.source(9L)).thenReturn(source);
        Bridge bridge=new Bridge();bridge.setPackingId(9L);bridge.setBatchId(20L);bridge.setSourceJson("{\"packingCode\":\"PACK-QA\"}");bridge.setSourceHash(payloads.payloadHash(bridge.getSourceJson()));when(packing.publicBridge(40L)).thenReturn(bridge);
        PackingEvidenceModels.Snapshot saved=new PackingEvidenceModels.Snapshot();saved.setManifestJson(payloads.canonicalJson(Map.of("source",Map.of("sourceHash",bridge.getSourceHash()))));saved.setManifestHash(payloads.payloadHash(saved.getManifestJson()));saved.setBatchPayload(payloads.canonicalJson(Map.of("batchCode","PACK-QA","productCode","PRODUCT-QA","metadata",Map.of("sourceHash",bridge.getSourceHash(),"evidenceHash",saved.getManifestHash()))));when(production.snapshot(9L)).thenReturn(saved);
        item.setId(40L);item.setBatchId(20L);item.setTraceCodePlain("CODE-QA");item.setTraceCodeHash(HashUtils.hashBusinessIdPlainHex("CODE-QA"));item.setTraceUrl("https://example.test/t/CODE-QA");item.setChainStatus("CONFIRMED");item.setStatus("ACTIVE");when(items.findById(40L)).thenReturn(item);
        when(items.findByTraceCodeHash(item.getTraceCodeHash())).thenReturn(item);
        ProductBatchEntity batch=new ProductBatchEntity();batch.setId(20L);batch.setBatchCode("PACK-QA");when(batches.findById(20L)).thenReturn(batch);
        chainBatch.setExists(true);chainBatch.setStatus(BigInteger.ONE);chainBatch.setProductIdHash(HashUtils.hashBusinessIdHex("PRODUCT-QA"));chainBatch.setPayloadHash(HashUtils.hashCanonicalTextHex(saved.getBatchPayload()));when(chain.getBatch("PACK-QA")).thenReturn(chainBatch);
        when(chain.getItem("CODE-QA")).thenAnswer(c->{ChainItemView v=new ChainItemView();v.setExists(true);v.setStatus(BigInteger.valueOf(chainItemStatus));v.setBatchIdHash(HashUtils.hashBusinessIdHex("PACK-QA"));v.setPayloadHash(payloads.payloadHashWithPrefix(Map.of("traceCodeHash",item.getTraceCodeHash(),"batchCode","PACK-QA","traceUrl",item.getTraceUrl())));v.setTraceEventCount(BigInteger.valueOf(live.size()));return v;});
        when(chain.getTraceEvent(eq("CODE-QA"),anyLong())).thenAnswer(c->live.get(((Long)c.getArgument(1)).intValue()));
        facts.put("reportId",5L);facts.put("report",payloads.readJson(report.getManifestJson()));facts.put("reportManifestHash",report.getManifestHash());facts.put("status","PUBLISHED");facts.put("attachmentPublic",false);facts.put("publishedAt",report.getPublishedAt());facts.put("revisionLinks",List.of());when(quality.publicFacts(5L,40L)).thenAnswer(c->new LinkedHashMap<>(facts));
        when(evidence.latest(5L,40L)).thenAnswer(c->versions.values().stream().reduce((a,b)->b).orElse(null));when(evidence.find(anyLong())).thenAnswer(c->versions.get(c.getArgument(0)));when(evidence.versions(5L,40L)).thenAnswer(c->{List<Evidence> rows=new ArrayList<>(versions.values());Collections.reverse(rows);return rows;});when(evidence.byVersion(eq(5L),eq(40L),anyInt())).thenAnswer(c->versions.values().stream().filter(v->v.getVersion().equals(c.getArgument(2))).findFirst().orElse(null));
        doAnswer(c->{Evidence r=c.getArgument(0);r.setId((long)versions.size()+1);r.setCreatedAt(LocalDateTime.now());versions.put(r.getId(),r);return 1;}).when(evidence).insert(any());
        doAnswer(c->{Evidence r=versions.get(c.getArgument(0));r.setLeaseToken(c.getArgument(1));r.setLeaseUntil(c.getArgument(2));return 1;}).when(evidence).claim(any(),any(),any());doAnswer(c->{Evidence r=versions.get(c.getArgument(0));if(Objects.equals(r.getLeaseToken(),c.getArgument(1))){r.setLeaseToken(null);r.setLeaseUntil(null);}return 1;}).when(evidence).release(any(),any());
        // MySQL JSON columns return normalized spacing, not the original canonical text.
        doAnswer(c->{TraceEventEntity e=c.getArgument(0);e.setId((long)local.size()+100);e.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payloads.readJson(e.getPayload())));local.put(e.getId(),e);return 1;}).when(events).insert(any());when(events.findById(any())).thenAnswer(c->local.get(c.getArgument(0)));
        doAnswer(c->{local.get(c.getArgument(0)).setChainStatus(c.getArgument(1));return 1;}).when(events).updateChainStatus(any(),any());
        when(transactions.createPending(any(),any(),any(),any(),any(),any(),any())).thenAnswer(c->{ChainTransactionEntity t=new ChainTransactionEntity();t.setId((long)txs.size()+1);t.setChainStatus("PENDING");txs.put(t.getId(),t);return t;});when(transactions.get(any())).thenAnswer(c->txs.get(c.getArgument(0)));when(transactions.completeFromResult(any(),any())).thenAnswer(c->{ChainTransactionEntity t=c.getArgument(0);ChainTxResult r=c.getArgument(1);t.setChainStatus(r.getChainStatus().name());t.setReceiptStatus(r.getReceiptStatus());return t;});
        when(chain.appendTraceEvent(any())).thenAnswer(c->{append(c.getArgument(0));return confirmed();});
    }
    @AfterEach void clear(){AccessContext.clear();}
    @BeforeEach void lockedRecord(){when(evidence.lockFrozen(anyLong())).thenAnswer(c->evidence.find(c.getArgument(0)));}
    @Test void reconciliationConfirmsFrozenReportWithoutResending() {
        service.submit(5L,request());Evidence row=versions.values().iterator().next();row.setChainStatus("UNCERTAIN");
        clearInvocations(chain,transactions);service.reconcileFrozen(row.getId(),row.getManifestHash(),row.getTxId());
        assertEquals("CONFIRMED",row.getChainStatus());verify(chain,never()).appendTraceEvent(any());verify(transactions,never()).createPending(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void reconciliationCannotSendMissingQualityVersion() {
        service.submit(5L,request());Evidence row=versions.values().iterator().next();live.clear();clearInvocations(chain,transactions);
        service.reconcileFrozen(row.getId(),row.getManifestHash(),row.getTxId());
        assertEquals("UNCERTAIN",row.getChainStatus());verify(chain,never()).appendTraceEvent(any());verify(transactions,never()).createPending(any(),any(),any(),any(),any(),any(),any());
    }
    private Submit request(){Submit r=new Submit();r.setTraceCode("CODE-QA");r.setExpectedManifestHash((String)service.preview(5L,"CODE-QA").get("manifestHash"));return r;}
    private ChainTxResult confirmed(){ChainTxResult r=new ChainTxResult();r.setChainStatus(ChainStatus.CONFIRMED);r.setReceiptStatus("0");return r;}
    private void append(AppendTraceEventCommand c){ChainTraceEventView v=new ChainTraceEventView();v.setIndex(BigInteger.valueOf(live.size()));v.setItemCodeHash(HashUtils.hashBusinessIdHex(c.getItemCode()));v.setEventIdHash(HashUtils.hashBusinessIdHex(c.getEventId()));v.setEventType(BigInteger.valueOf(c.getEventType()));v.setOccurredAt(BigInteger.valueOf(c.getOccurredAt()));v.setPayloadHash(payloads.payloadHashWithPrefix(c.getPayload()));live.add(v);}
    @Test void previewDoesNotFreezeOrWriteChain(){service.preview(5L,"CODE-QA");assertTrue(versions.isEmpty());verify(events,never()).insert(any());verify(chain,never()).appendTraceEvent(any());}
    @Test void unpublishedAndUnknownCodeCannotFreeze(){report.setPublishedAt(null);assertThrows(IllegalArgumentException.class,()->service.preview(5L,"CODE-QA"));report.setPublishedAt(LocalDateTime.now());assertThrows(IllegalArgumentException.class,()->service.preview(5L,"UNKNOWN"));assertTrue(versions.isEmpty());}
    @Test void attachmentIntegrityIsCheckedBeforeFreezing(){doThrow(new IllegalArgumentException("file changed")).when(quality).privateContent(5L);assertThrows(IllegalArgumentException.class,()->service.submit(5L,request()));assertTrue(versions.isEmpty());verify(chain,never()).appendTraceEvent(any());}
    @Test void proofIsNotInheritedByAnotherReportOrAnotherCode(){service.submit(5L,request());assertNull(service.publicEvidence(6L,40L,new ArrayList<>(local.values()),new PublicTraceView.ChainVerification()));assertNull(service.publicEvidence(5L,41L,new ArrayList<>(local.values()),new PublicTraceView.ChainVerification()));}
    @Test void freezesAfterLockingHarvestReportAndPackaging(){service.submit(5L,request());org.mockito.InOrder order=inOrder(reports,packing);order.verify(reports).lockHarvest(17L);order.verify(reports).lock(5L);order.verify(packing).lock(9L);}
    @Test void intentAndSnapshotPersistBeforeExternalWrite(){doAnswer(c->{assertEquals(1,versions.size());assertNotNull(versions.get(1L).getTxId());assertEquals("PENDING",local.get(100L).getChainStatus());append(c.getArgument(0));return confirmed();}).when(chain).appendTraceEvent(any());service.submit(5L,request());assertEquals("CONFIRMED",versions.get(1L).getChainStatus());assertNull(versions.get(1L).getLeaseToken());}
    @Test void repeatSubmissionReadsExistingEventAndDoesNotAppendAgain(){Submit r=request();service.submit(5L,r);service.submit(5L,r);assertEquals(1,versions.size());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void staleConfirmationRejectsBeforeFreeze(){Submit r=request();facts.put("status","WITHDRAWN");assertThrows(IllegalArgumentException.class,()->service.submit(5L,r));assertTrue(versions.isEmpty());}
    @Test void writeRolesOrganizationAndProductionReadinessAreChecked(){Submit r=request();actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.submit(5L,r));actor.setRole("MANAGER");actor.setOrganizationId(8L);assertThrows(AccessException.class,()->service.preview(5L,"CODE-QA"));actor.setOrganizationId(3L);item.setChainStatus("NOT_SUBMITTED");assertThrows(IllegalArgumentException.class,()->service.submit(5L,r));verify(chain,never()).appendTraceEvent(any());}
    @Test void unknownTransactionIsNotBlindlyRetried(){doThrow(new IllegalStateException("timeout")).when(chain).appendTraceEvent(any());Submit r=request();service.submit(5L,r);service.submit(5L,r);assertEquals("UNCERTAIN",versions.get(1L).getChainStatus());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void unknownVersionCanRecoverEvenWhenBusinessHasNewFacts(){
        doThrow(new IllegalStateException("timeout")).when(chain).appendTraceEvent(any());Submit original=request();service.submit(5L,original);facts.put("status","WITHDRAWN");assertThrows(IllegalArgumentException.class,()->service.submit(5L,request()));
        AppendTraceEventCommand c=new AppendTraceEventCommand();TraceEventEntity e=local.get(100L);c.setItemCode("CODE-QA");c.setEventId("TRACE_EVENT:100");c.setEventType(5);c.setOccurredAt(e.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());c.setPayload(payloads.readJson(e.getPayload()));append(c);
        service.submit(5L,original);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());assertFalse((Boolean)service.preview(5L,"CODE-QA").get("frozen"));verify(chain,times(1)).appendTraceEvent(any());
    }
    @Test void sourceMismatchDoesNotAppend(){chainBatch.setPayloadHash("0xwrong");service.submit(5L,request());assertEquals("SOURCE_MISMATCH",versions.get(1L).getErrorCode());verify(chain,never()).appendTraceEvent(any());}
    @Test void inactiveChainSourceStopsPublication(){chainBatch.setStatus(BigInteger.valueOf(3));service.submit(5L,request());assertEquals("SOURCE_NOT_ACTIVE",versions.get(1L).getErrorCode());verify(chain,never()).appendTraceEvent(any());}
    @Test void recalledItemBlocksNewWritesButDoesNotEraseExistingProof(){Submit r=request();service.submit(5L,r);chainItemStatus=4;service.submit(5L,r);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());facts.put("status","WITHDRAWN");service.submit(5L,request());assertEquals("SOURCE_NOT_ACTIVE",versions.get(2L).getErrorCode());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void conflictingExistingEventIsNeverOverwritten(){doAnswer(c->{append(c.getArgument(0));live.get(0).setPayloadHash("0xwrong");throw new IllegalStateException("timeout");}).when(chain).appendTraceEvent(any());Submit r=request();service.submit(5L,r);service.submit(5L,r);assertEquals("HASH_MISMATCH",versions.get(1L).getErrorCode());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void chainDisabledMayBeExplicitlyRetried(){doReturn(ChainTxResult.disabled("0xcontract","disabled")).when(chain).appendTraceEvent(any());Submit r=request();service.submit(5L,r);assertEquals("CHAIN_DISABLED",versions.get(1L).getChainStatus());doAnswer(c->{append(c.getArgument(0));return confirmed();}).when(chain).appendTraceEvent(any());service.submit(5L,r);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());}
    @Test void definiteFailedReceiptCanRetry(){ChainTxResult failure=new ChainTxResult();failure.setChainStatus(ChainStatus.FAILED);failure.setReceiptStatus("16");doReturn(failure).when(chain).appendTraceEvent(any());Submit r=request();service.submit(5L,r);doAnswer(c->{append(c.getArgument(0));return confirmed();}).when(chain).appendTraceEvent(any());service.submit(5L,r);assertEquals("CONFIRMED",versions.get(1L).getChainStatus());}
    @Test void failureWithoutReceiptIsUnknownAndNotRetried(){ChainTxResult failure=new ChainTxResult();failure.setChainStatus(ChainStatus.FAILED);doReturn(failure).when(chain).appendTraceEvent(any());Submit r=request();service.submit(5L,r);service.submit(5L,r);assertEquals("UNCERTAIN",versions.get(1L).getChainStatus());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void unavailableReadDoesNotSend(){when(chain.getBatch(any())).thenThrow(new IllegalStateException("offline"));service.submit(5L,request());assertEquals("NOT_SUBMITTED",versions.get(1L).getChainStatus());verify(chain,never()).appendTraceEvent(any());}
    @Test void leaseBlocksConcurrentSubmission(){Submit r=request();service.submit(5L,r);versions.get(1L).setLeaseUntil(LocalDateTime.now().plusMinutes(1));assertThrows(IllegalArgumentException.class,()->service.submit(5L,r));verify(chain,times(1)).appendTraceEvent(any());}
    @Test void newVersionPreservesOldSnapshotAndLinksItsDigest(){service.submit(5L,request());String json=versions.get(1L).getManifestJson(),hash=versions.get(1L).getManifestHash();facts.put("status","WITHDRAWN");service.submit(5L,request());assertEquals(2,versions.size());assertEquals(json,versions.get(1L).getManifestJson());assertEquals(hash,versions.get(2L).getPreviousHash());assertEquals("CONFIRMED",versions.get(2L).getChainStatus());}
    @Test void versionLimitKeepsRecoveryAvailableWithoutAbsorbingNewFacts(){for(int i=1;i<=20;i++){facts.put("revisionLinks",List.of(Map.of("kind","UPDATE","occurredAt","2026-10-25T10:"+String.format("%02d",i)+":00")));service.submit(5L,request());}facts.put("revisionLinks",List.of(Map.of("kind","UPDATE","occurredAt","2026-10-25T11:00:00")));Map<String,Object> preview=service.preview(5L,"CODE-QA");assertEquals(true,preview.get("versionLimitReached"));service.submit(5L,request());assertEquals(20,versions.size());verify(chain,times(20)).appendTraceEvent(any());}
    @Test void missingLiveParentPreventsSkippingHistory(){service.submit(5L,request());live.clear();facts.put("status","WITHDRAWN");service.submit(5L,request());assertEquals("PREVIOUS_NOT_VERIFIED",versions.get(2L).getErrorCode());verify(chain,times(1)).appendTraceEvent(any());}
    @Test void damagedFrozenEvidenceCannotBePresentedOrResubmitted(){Submit r=request();service.submit(5L,r);versions.get(1L).setManifestJson("{}");assertThrows(IllegalArgumentException.class,()->service.preview(5L,"CODE-QA"));assertThrows(IllegalArgumentException.class,()->service.submit(5L,r));}
    @Test void publicVerificationDoesNotExtendOldProofToNewFacts(){
        service.submit(5L,request());PublicTraceView.ChainVerification v=new PublicTraceView.ChainVerification();v.setVerificationSource("LIVE_CHAIN");v.setBatchPayloadHashMatched(true);v.setItemPayloadHashMatched(true);List<PublicTraceView.Record> records=new ArrayList<>();for(int i=0;i<3;i++){PublicTraceView.Record r=new PublicTraceView.Record();r.setVerificationSource("LIVE_CHAIN");r.setPayloadHashMatched(true);records.add(r);}v.setRecords(records);
        Map<String,Object> result=service.publicEvidence(5L,40L,new ArrayList<>(local.values()),v);assertEquals("VERIFIED",result.get("verificationStatus"));facts.put("status","WITHDRAWN");result=service.publicEvidence(5L,40L,new ArrayList<>(local.values()),v);assertEquals("NOT_VERIFIED",result.get("verificationStatus"));assertEquals(false,result.get("currentMatchesLatest"));v.setVerificationSource("UNAVAILABLE");result=service.publicEvidence(5L,40L,new ArrayList<>(local.values()),v);assertEquals("NOT_VERIFIED",((Map<?,?>)((List<?>)result.get("versions")).get(0)).get("verificationStatus"));
    }
    @Test void publicLatestVersionRequiresItsPredecessorToBeLiveVerified(){
        service.submit(5L,request());facts.put("status","WITHDRAWN");service.submit(5L,request());
        PublicTraceView.ChainVerification v=new PublicTraceView.ChainVerification();v.setVerificationSource("LIVE_CHAIN");v.setBatchPayloadHashMatched(true);v.setItemPayloadHashMatched(true);List<PublicTraceView.Record> records=new ArrayList<>();for(int i=0;i<4;i++){PublicTraceView.Record r=new PublicTraceView.Record();r.setVerificationSource("LIVE_CHAIN");r.setPayloadHashMatched(i!=2);records.add(r);}v.setRecords(records);
        Map<String,Object> result=service.publicEvidence(5L,40L,new ArrayList<>(local.values()),v);assertEquals("NOT_VERIFIED",result.get("verificationStatus"));records.get(2).setPayloadHashMatched(true);assertEquals("VERIFIED",service.publicEvidence(5L,40L,new ArrayList<>(local.values()),v).get("verificationStatus"));
    }
}
