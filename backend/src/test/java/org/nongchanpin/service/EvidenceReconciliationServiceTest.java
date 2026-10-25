package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingEvidenceModels.*;
import org.nongchanpin.model.entity.PackingTraceModels.Bridge;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;

class EvidenceReconciliationServiceTest {
    private final EvidenceReconciliationMapper records=mock(EvidenceReconciliationMapper.class);
    private final PackingEvidenceMapper production=mock(PackingEvidenceMapper.class);
    private final CirculationEvidenceMapper circulation=mock(CirculationEvidenceMapper.class);
    private final QualityEvidenceMapper quality=mock(QualityEvidenceMapper.class);
    private final PackingNfcMapper nfc=mock(PackingNfcMapper.class);
    private final NfcTagMapper tags=mock(NfcTagMapper.class);
    private final PackingTraceMapper packing=mock(PackingTraceMapper.class);
    private final TraceItemMapper items=mock(TraceItemMapper.class);
    private final ProductBatchMapper batches=mock(ProductBatchMapper.class);
    private final TraceEventMapper events=mock(TraceEventMapper.class);
    private final ChainTransactionService transactions=mock(ChainTransactionService.class);
    private final PackingEvidenceService productionService=mock(PackingEvidenceService.class);
    private final CirculationEvidenceService circulationService=mock(CirculationEvidenceService.class);
    private final QualityEvidenceService qualityService=mock(QualityEvidenceService.class);
    private final PackingNfcService nfcService=mock(PackingNfcService.class);
    private final PayloadService payloads=new PayloadService();
    private final ChainService chain=mock(ChainService.class);
    private final EvidenceReconciliationService service=new EvidenceReconciliationService(records,production,circulation,quality,nfc,tags,packing,items,batches,events,transactions,productionService,circulationService,qualityService,nfcService,payloads,chain);
    private final EvidenceReconciliationRow row=new EvidenceReconciliationRow();
    private final Actor actor=new Actor();
    private final ChainBatchView chainBatch=new ChainBatchView();
    private final ChainItemView chainItem=new ChainItemView();
    private final ChainTransactionEntity intent=new ChainTransactionEntity();
    private final Job job=new Job();
    private final Snapshot snapshot=new Snapshot();
    private final TraceEventEntity event=new TraceEventEntity();
    private final List<ChainTraceEventView> live=new ArrayList<>();

    @BeforeEach void setup() {
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        row.setKind("PRODUCTION");row.setRecordId(7L);row.setOrganizationId(3L);row.setPackingId(9L);row.setItemId(40L);row.setAction("ITEM_BIND");row.setVersion(1);row.setChainStatus("UNCERTAIN");row.setTxId(10L);
        when(records.find(anyString(),anyLong(),nullable(Long.class))).thenAnswer(c->row.getKind().equals(c.getArgument(0))&&row.getRecordId().equals(c.getArgument(1))&&(c.getArgument(2)==null||row.getOrganizationId().equals(c.getArgument(2)))?row:null);
        Bridge bridge=new Bridge();bridge.setPackingId(9L);bridge.setBatchId(20L);bridge.setSourceHash("a".repeat(64));when(packing.bridge(9L)).thenReturn(bridge);
        snapshot.setManifestJson(payloads.canonicalJson(Map.of("source",Map.of("sourceHash",bridge.getSourceHash()))));snapshot.setManifestHash(payloads.payloadHash(snapshot.getManifestJson()));
        snapshot.setBatchPayload(payloads.canonicalJson(Map.of("productCode","P-QA","metadata",Map.of("sourceHash",bridge.getSourceHash(),"evidenceHash",snapshot.getManifestHash()))));when(production.snapshot(9L)).thenReturn(snapshot);
        ProductBatchEntity batch=new ProductBatchEntity();batch.setId(20L);batch.setBatchCode("PACK-QA");when(batches.findById(20L)).thenReturn(batch);
        TraceItemEntity item=new TraceItemEntity();item.setId(40L);item.setBatchId(20L);item.setTraceCodePlain("CODE-QA");item.setTraceCodeHash(HashUtils.hashBusinessIdPlainHex("CODE-QA"));item.setTraceUrl("https://example.test/t/CODE-QA");when(items.findById(40L)).thenReturn(item);
        row.setFrozenHash(payloads.payloadHashWithPrefix(Map.of("traceCodeHash",item.getTraceCodeHash(),"batchCode",batch.getBatchCode(),"traceUrl",item.getTraceUrl())));
        job.setId(7L);job.setPayloadHash(row.getFrozenHash());job.setTxId(10L);when(production.findJob(7L)).thenReturn(job);
        intent.setId(10L);intent.setChainStatus("PENDING");when(transactions.get(10L)).thenReturn(intent);
        chainBatch.setExists(true);chainBatch.setPayloadHash(HashUtils.hashCanonicalTextHex(snapshot.getBatchPayload()));chainBatch.setProductIdHash(HashUtils.hashBusinessIdHex("P-QA"));when(chain.getBatch("PACK-QA")).thenReturn(chainBatch);
        chainItem.setExists(true);chainItem.setBatchIdHash(HashUtils.hashBusinessIdHex("PACK-QA"));chainItem.setPayloadHash(row.getFrozenHash());chainItem.setTraceEventCount(BigInteger.ZERO);when(chain.getItem("CODE-QA")).thenReturn(chainItem);
        event.setId(100L);event.setItemId(40L);event.setEventType("LOGISTICS");event.setCanonicalPayloadHash("b".repeat(64));event.setEventTime(LocalDateTime.of(2026,10,25,10,0));when(events.findById(100L)).thenReturn(event);
        when(chain.getTraceEvent(eq("CODE-QA"),anyLong())).thenAnswer(c->live.get(((Long)c.getArgument(1)).intValue()));
    }
    @AfterEach void clear(){AccessContext.clear();}
    private Map<String,Object> inspect(){return service.inspect(row.getKind(),row.getRecordId());}
    private String hash(){return (String)inspect().get("checkHash");}
    private void noChainWrites(){verify(chain,never()).registerBatch(any());verify(chain,never()).bindItem(any());verify(chain,never()).appendTraceEvent(any());verify(chain,never()).bindNfcTag(any());verify(chain,never()).updateNfcStatus(any());verify(transactions,never()).createPending(any(),any(),any(),any(),any(),any(),any());}
    private void eventFixture(String kind) {
        row.setKind(kind);row.setReportId(5L);row.setAction("QUALITY".equals(kind)?"QUALITY_TEST":"TRACE_EVENT");row.setFrozenHash("c".repeat(64));
        if("QUALITY".equals(kind))event.setEventType("QUALITY_TEST");
        CirculationEvidenceEntity flow=new CirculationEvidenceEntity();flow.setId(7L);flow.setVersion(1);flow.setEventId(100L);flow.setManifestHash(row.getFrozenHash());flow.setTxId(10L);
        when(circulation.find(7L)).thenReturn(flow);when(circulation.versions(40L)).thenReturn(List.of(flow));
        QualityEvidenceModels.Evidence report=new QualityEvidenceModels.Evidence();report.setId(7L);report.setVersion(1);report.setEventId(100L);report.setManifestHash(row.getFrozenHash());report.setTxId(10L);
        when(quality.find(7L)).thenReturn(report);when(quality.versions(5L,40L)).thenReturn(List.of(report));
        ChainTraceEventView actual=new ChainTraceEventView();actual.setIndex(BigInteger.ZERO);actual.setEventIdHash(HashUtils.hashBusinessIdHex("TRACE_EVENT:100"));actual.setItemCodeHash(HashUtils.hashBusinessIdHex("CODE-QA"));actual.setPayloadHash("0x"+event.getCanonicalPayloadHash());actual.setEventType(BigInteger.valueOf("QUALITY".equals(kind)?5:8));actual.setOccurredAt(BigInteger.valueOf(event.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond()));
        live.add(actual);chainItem.setTraceEventCount(BigInteger.ONE);
    }
    private void nfcFixture(String action) {
        row.setKind("NFC");row.setAction(action);row.setFrozenHash("d".repeat(64));
        PackingNfcOperation bind=new PackingNfcOperation();bind.setId(7L);bind.setItemId(40L);bind.setTagId(6L);bind.setPayloadHash(row.getFrozenHash());bind.setTxId(10L);
        when(nfc.find(7L)).thenReturn(bind);when(nfc.operation(40L,"BIND")).thenReturn(bind);
        NfcTagEntity tag=new NfcTagEntity();tag.setId(6L);tag.setUidHash("e".repeat(64));when(tags.findById(6L)).thenReturn(tag);
        chainItem.setNfcUidHash("0x"+tag.getUidHash());ChainNfcView actual=new ChainNfcView();actual.setExists(true);actual.setStatus(BigInteger.ONE);actual.setNfcUidHash(chainItem.getNfcUidHash());actual.setItemCodeHash(HashUtils.hashBusinessIdHex("CODE-QA"));actual.setPayloadHash("0x"+bind.getPayloadHash());when(chain.getNfcTag(anyString())).thenReturn(actual);
    }

    @Test void listPassesOrganizationAndDoesNotReadChain(){when(records.count(any(),any(),any(),any(),any(),any())).thenReturn(25L);when(records.rows(any(),any(),any(),any(),any(),any(),anyLong())).thenReturn(List.of(row));Map<String,Object> result=service.list("","ATTENTION",null,"",24,9);assertEquals(2L,result.get("page"));verify(records).rows(eq(3L),eq(""),eq("ATTENTION"),isNull(),eq(""),any(),eq(20L));verifyNoInteractions(chain);}
    @Test void invalidFiltersAreRejectedBeforeQuery(){assertThrows(IllegalArgumentException.class,()->service.list("UNKNOWN","ALL",null,"",0,1));assertThrows(IllegalArgumentException.class,()->service.list("","ALL",null,"",-1,1));assertThrows(IllegalArgumentException.class,()->service.list("","ALL",null,"",0,0));verifyNoInteractions(records,chain);}
    @Test void unknownOrForeignRecordsDoNotReadChain(){actor.setOrganizationId(4L);assertThrows(AccessException.class,this::inspect);verifyNoInteractions(chain);}
    @Test void viewerCanInspectButCannotRecord(){actor.setRole("VIEWER");assertEquals("MATCHED",inspect().get("outcome"));assertThrows(AccessException.class,()->service.reconcile("PRODUCTION",7L,hash()));verify(productionService,never()).reconcileFrozen(any(),any(),any());noChainWrites();}
    @Test void liveMatchedRecordAllowsExplicitLocalRecovery(){Map<String,Object> result=inspect();assertEquals("MATCHED",result.get("outcome"));assertEquals(true,result.get("canRecord"));assertEquals("PENDING",result.get("transactionStatus"));service.reconcile("PRODUCTION",7L,(String)result.get("checkHash"));verify(productionService).reconcileFrozen(7L,row.getFrozenHash(),10L);verify(transactions,never()).markConfirmed(any(),any());noChainWrites();}
    @Test void confirmedLocalRecordDoesNotNeedAnotherWrite(){row.setChainStatus("CONFIRMED");assertEquals(false,inspect().get("canRecord"));assertThrows(IllegalArgumentException.class,()->service.reconcile("PRODUCTION",7L,hash()));noChainWrites();}
    @Test void processingLeaseBlocksLocalRecovery(){row.setLeaseUntil(LocalDateTime.now().plusMinutes(1));assertEquals(true,inspect().get("processing"));assertEquals(false,inspect().get("canRecord"));}
    @Test void changingOriginalIntentInvalidatesConfirmation(){String old=hash();intent.setChainStatus("FAILED");intent.setReceiptStatus("1");assertThrows(IllegalArgumentException.class,()->service.reconcile("PRODUCTION",7L,old));verify(productionService,never()).reconcileFrozen(any(),any(),any());noChainWrites();}
    @Test void chainDisappearingInvalidatesConfirmation(){String old=hash();chainItem.setExists(false);assertThrows(IllegalArgumentException.class,()->service.reconcile("PRODUCTION",7L,old));assertEquals("UNKNOWN_TRANSACTION",inspect().get("disposition"));noChainWrites();}
    @Test void absentWithoutIntentRequiresBusinessReviewRatherThanSending(){row.setTxId(null);job.setTxId(null);chainItem.setExists(false);Map<String,Object> result=inspect();assertEquals("NOT_FOUND",result.get("outcome"));assertEquals("BUSINESS_REVIEW",result.get("disposition"));assertEquals(false,result.get("canRecord"));noChainWrites();}
    @Test void definiteFailureCanOnlyReturnToBusinessConfirmation(){chainItem.setExists(false);intent.setChainStatus("FAILED");intent.setReceiptStatus("1");assertEquals("BUSINESS_REVIEW",inspect().get("disposition"));noChainWrites();}
    @Test void unavailableReadIsNotTreatedAsAbsent(){when(chain.getBatch(anyString())).thenThrow(new IllegalStateException());assertEquals("UNAVAILABLE",inspect().get("outcome"));assertEquals(false,inspect().get("canRecord"));noChainWrites();}
    @Test void mismatchedSourceIsAConflict(){chainBatch.setPayloadHash("0x"+"f".repeat(64));assertEquals("CONFLICT",inspect().get("outcome"));noChainWrites();}
    @Test void missingOriginalIntentIsALocalConflict(){when(transactions.get(10L)).thenThrow(new IllegalArgumentException());assertEquals("LOCAL_CONFLICT",inspect().get("outcome"));noChainWrites();}
    @Test void invalidLocalSnapshotIsNotReportedAsNodeFailure(){snapshot.setManifestHash("0".repeat(64));assertEquals("LOCAL_CONFLICT",inspect().get("outcome"));assertEquals(false,inspect().get("canRecord"));}
    @Test void alteredFrozenHashIsALocalConflict(){job.setPayloadHash("f".repeat(64));assertEquals("LOCAL_CONFLICT",inspect().get("outcome"));}
    @Test void batchRegistrationIsReadSeparatelyFromItems(){row.setAction("BATCH_REGISTER");row.setItemId(null);assertEquals("MATCHED",inspect().get("outcome"));verify(chain,never()).getItem(anyString());}
    @Test void bothFlowAndQualityRequireLiveEvents(){for(String kind:List.of("CIRCULATION","QUALITY")){live.clear();eventFixture(kind);assertEquals("MATCHED",inspect().get("outcome"));service.reconcile(kind,7L,hash());}verify(circulationService).reconcileFrozen(7L,row.getFrozenHash(),10L);verify(qualityService).reconcileFrozen(7L,row.getFrozenHash(),10L);noChainWrites();}
    @Test void wrongEventTypeOrTimeIsAConflict(){eventFixture("QUALITY");live.get(0).setEventType(BigInteger.valueOf(8));assertEquals("CONFLICT",inspect().get("outcome"));noChainWrites();}
    @Test void absentEventsDoNotPermitRecovery(){eventFixture("CIRCULATION");chainItem.setTraceEventCount(BigInteger.ZERO);assertEquals("NOT_FOUND",inspect().get("outcome"));assertEquals(false,inspect().get("canRecord"));}
    @Test void readLimitDoesNotClaimEventsAreAbsent(){eventFixture("QUALITY");chainItem.setTraceEventCount(BigInteger.valueOf(201));assertEquals("READ_LIMIT",inspect().get("outcome"));noChainWrites();}
    @Test void missingParentIsNotFullVerification(){eventFixture("QUALITY");QualityEvidenceModels.Evidence target=quality.find(7L);target.setVersion(2);row.setVersion(2);QualityEvidenceModels.Evidence parent=new QualityEvidenceModels.Evidence();parent.setId(6L);parent.setVersion(1);parent.setEventId(99L);TraceEventEntity old=new TraceEventEntity();old.setId(99L);when(events.findById(99L)).thenReturn(old);when(quality.versions(5L,40L)).thenReturn(List.of(target,parent));assertEquals("PREVIOUS_MISSING",inspect().get("outcome"));noChainWrites();}
    @Test void nfcBindingIsIndependentlyReadAndCanRecover(){nfcFixture("BIND");assertEquals("MATCHED",inspect().get("outcome"));service.reconcile("NFC",7L,hash());verify(nfcService).reconcileFrozen(7L,row.getFrozenHash(),10L);noChainWrites();}
    @Test void nfcDisableNeedsDisabledStatusAndMatchingReasonDigest(){nfcFixture("DISABLE");assertEquals("NOT_FOUND",inspect().get("outcome"));ChainNfcView actual=chain.getNfcTag("0x"+"e".repeat(64));actual.setStatus(BigInteger.valueOf(3));actual.setReasonHash("0x"+row.getFrozenHash());assertEquals("MATCHED",inspect().get("outcome"));actual.setReasonHash("0x"+"f".repeat(64));assertEquals("CONFLICT",inspect().get("outcome"));noChainWrites();}
}
