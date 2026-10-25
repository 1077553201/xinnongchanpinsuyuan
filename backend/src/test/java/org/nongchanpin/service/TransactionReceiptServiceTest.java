package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.ReceiptCall;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingEvidenceModels.Job;
import org.nongchanpin.model.entity.PackingTraceModels.Bridge;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.transaction.*;

class TransactionReceiptServiceTest {
    private final EvidenceReconciliationService evidence=mock(EvidenceReconciliationService.class);
    private final ChainTransactionMapper transactions=mock(ChainTransactionMapper.class);
    private final PackingEvidenceMapper production=mock(PackingEvidenceMapper.class);
    private final PackingTraceMapper packing=mock(PackingTraceMapper.class);
    private final CirculationEvidenceMapper circulation=mock(CirculationEvidenceMapper.class);
    private final QualityEvidenceMapper quality=mock(QualityEvidenceMapper.class);
    private final SourceQualityMapper reports=mock(SourceQualityMapper.class);
    private final PackingNfcMapper nfc=mock(PackingNfcMapper.class);
    private final NfcTagMapper tags=mock(NfcTagMapper.class);
    private final TraceItemMapper items=mock(TraceItemMapper.class);
    private final ProductBatchMapper batches=mock(ProductBatchMapper.class);
    private final TraceEventMapper events=mock(TraceEventMapper.class);
    private final PayloadService payloads=new PayloadService();private final ChainService chain=mock(ChainService.class);
    private final ContractConfig config=new ContractConfig();private final AuditService audit=mock(AuditService.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final TransactionReceiptService service=new TransactionReceiptService(evidence,transactions,production,packing,circulation,quality,reports,nfc,tags,items,batches,events,payloads,chain,config,audit,manager);
    private final EvidenceReconciliationRow row=new EvidenceReconciliationRow();private final ChainTransactionEntity intent=new ChainTransactionEntity();
    private final Job job=new Job();private final ProductBatchEntity batch=new ProductBatchEntity();private final TraceItemEntity item=new TraceItemEntity();private final Actor actor=new Actor();
    private ChainReceiptView actual;private ReceiptCall expected;
    @BeforeEach void setup(){
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);config.setAgriTraceAddress("0x"+"1".repeat(40));
        row.setKind("PRODUCTION");row.setRecordId(7L);row.setPackingId(9L);row.setItemId(40L);row.setTxId(10L);row.setChainStatus("UNCERTAIN");
        when(evidence.required(anyString(),anyLong())).thenAnswer(c->{AccessContext.checkOrganization(3L);return row;});
        Bridge bridge=new Bridge();bridge.setPackingId(9L);bridge.setBatchId(20L);when(packing.bridge(9L)).thenReturn(bridge);
        batch.setId(20L);batch.setBatchCode("PACK-QA");batch.setCreateTime(LocalDateTime.of(2026,10,25,10,0));when(batches.findById(20L)).thenReturn(batch);
        item.setId(40L);item.setTraceCodePlain("CODE-QA");when(items.findById(40L)).thenReturn(item);
        job.setId(7L);job.setBusinessType("ITEM_BIND");job.setBusinessId(40L);job.setPayloadJson("{\"qa\":true}");job.setPayloadHash(HashUtils.hashCanonicalTextHex(job.getPayloadJson()));when(production.findJob(7L)).thenReturn(job);row.setFrozenHash(job.getPayloadHash());
        intent.setId(10L);intent.setBusinessType("ITEM_BIND");intent.setBusinessId(40L);intent.setBusinessIdHash(HashUtils.hashBusinessIdHex("CODE-QA"));intent.setContractAddress(config.getAgriTraceAddress());intent.setEventName("ItemBound");intent.setPayloadHash(job.getPayloadHash());intent.setRequestHash(HashUtils.hashCanonicalTextPlainHex(job.getPayloadJson()));intent.setTxHash("0x"+"a".repeat(64));intent.setChainStatus("PENDING");
        when(transactions.findById(10L)).thenReturn(intent);when(transactions.lockReceipt(10L)).thenReturn(intent);when(transactions.recordReceipt(any())).thenReturn(1);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(chain.inspectReceipt(any())).thenAnswer(c->{expected=c.getArgument(0);if(actual!=null)return actual;return matching(expected);});
    }
    @AfterEach void clear(){AccessContext.clear();}
    private ChainReceiptView matching(ReceiptCall call){ChainReceiptView r=new ChainReceiptView();r.setFound(true);r.setTransactionHash(call.getTxHash());r.setContractAddress(call.getContractAddress());r.setBlockNumber(BigInteger.TEN);r.setOperator("0x"+"2".repeat(40));r.setReceiptStatus(0);r.setInputMatched(true);r.setInputHash("d".repeat(64));Map<String,String> event=new LinkedHashMap<>(call.getEventFields());event.put("operator",r.getOperator());r.getEvents().add(event);return r;}
    private Map<String,Object> preview(){return service.preview(row.getKind(),7L);}
    private String hash(){return (String)preview().get("checkHash");}
    private void noSend(){verify(chain,never()).bindItem(any());verify(chain,never()).registerBatch(any());verify(chain,never()).appendTraceEvent(any());verify(chain,never()).bindNfcTag(any());verify(chain,never()).updateNfcStatus(any());}
    @Test void previewReadsOriginalHashAndDoesNotWrite(){assertEquals("SUCCESS_MATCHED",preview().get("outcome"));assertEquals("bindItem",expected.getMethod());assertEquals(3,expected.getArguments().size());verify(transactions,never()).recordReceipt(any());verifyNoInteractions(audit);noSend();}
    @Test void missingIntentCannotQueryArbitraryReceipts(){row.setTxId(null);assertEquals("NO_INTENT",preview().get("outcome"));verifyNoInteractions(chain);}
    @Test void missingHashIsNotGuessedFromExistingBusinessEvidence(){intent.setTxHash(null);assertEquals("MISSING_HASH",preview().get("outcome"));assertEquals(false,preview().get("canRecord"));verifyNoInteractions(chain);}
    @Test void invalidHashIsStopped(){intent.setTxHash("wrong");assertEquals("INVALID_HASH",preview().get("outcome"));verifyNoInteractions(chain);}
    @Test void differentTargetContractIsStopped(){intent.setContractAddress("0x"+"3".repeat(40));assertEquals("LOCAL_CONFLICT",preview().get("outcome"));verifyNoInteractions(chain);}
    @Test void mismatchedOriginalIntentIsStopped(){intent.setBusinessId(41L);assertEquals("LOCAL_CONFLICT",preview().get("outcome"));verifyNoInteractions(chain);}
    @Test void unavailableNodeDoesNotChangeIntent(){doThrow(new IllegalStateException()).when(chain).inspectReceipt(any());assertEquals("UNAVAILABLE",preview().get("outcome"));assertEquals("PENDING",intent.getChainStatus());verify(transactions,never()).recordReceipt(any());}
    @Test void viewerReadsButCannotRecord(){actor.setRole("VIEWER");String hash=hash();assertThrows(AccessException.class,()->service.record(row.getKind(),7L,hash));verify(transactions,never()).recordReceipt(any());}
    @Test void foreignOrganizationCannotReadReceipt(){actor.setOrganizationId(4L);assertThrows(AccessException.class,this::preview);verifyNoInteractions(chain);}
    @Test void explicitRecordPreservesHashSubmissionAndBusinessState(){LocalDateTime submitted=LocalDateTime.of(2026,10,25,9,0);intent.setSubmittedAt(submitted);String originalHash=intent.getTxHash();Map<String,Object> saved=service.record(row.getKind(),7L,hash());assertEquals(true,saved.get("alreadyRecorded"));assertEquals(false,saved.get("canRecord"));assertEquals("CONFIRMED",intent.getChainStatus());assertEquals(originalHash,intent.getTxHash());assertEquals(submitted,intent.getSubmittedAt());assertEquals("UNCERTAIN",row.getChainStatus());assertNotNull(intent.getReceiptVerifiedAt());verify(audit).record(eq("TRANSACTION_RECEIPT_RECORD"),eq("PRODUCTION"),eq(7L),anyString());noSend();}
    @Test void confirmedReceiptCannotBeRecordedAgain(){service.record(row.getKind(),7L,hash());assertThrows(IllegalArgumentException.class,()->service.record(row.getKind(),7L,hash()));verify(transactions,times(1)).recordReceipt(any());}
    @Test void alteredReceiptInvalidatesConfirmation(){String old=hash();actual=matching(expected);actual.setBlockNumber(BigInteger.valueOf(11));assertThrows(IllegalArgumentException.class,()->service.record(row.getKind(),7L,old));verify(transactions,never()).recordReceipt(any());}
    @Test void lockedIntentMustStillBeTheSame(){String hash=hash();doAnswer(c->{intent.setTxHash("0x"+"f".repeat(64));return null;}).when(packing).lock(9L);assertThrows(IllegalArgumentException.class,()->service.record(row.getKind(),7L,hash));verify(transactions,never()).recordReceipt(any());}
    @Test void activeLeaseBlocksReceiptRecording(){row.setLeaseUntil(LocalDateTime.now().plusMinutes(1));assertEquals(false,preview().get("canRecord"));}
    @Test void conflictingAlreadyRecordedReceiptIsNotOverwritten(){service.record(row.getKind(),7L,hash());actual=matching(expected);actual.setBlockNumber(BigInteger.valueOf(11));assertEquals("RECORDED_CONFLICT",preview().get("outcome"));assertEquals(false,preview().get("canRecord"));}
    @Test void actualFailureRecordsOnlyOriginalTransactionFailure(){preview();actual=matching(expected);actual.setReceiptStatus(16);actual.getEvents().clear();assertEquals("FAILED_MATCHED",preview().get("outcome"));service.record(row.getKind(),7L,hash());assertEquals("FAILED",intent.getChainStatus());assertEquals("16",intent.getReceiptStatus());assertEquals("UNCERTAIN",row.getChainStatus());noSend();}
    @Test void batchExpectationBindsOriginalStartedAt(){row.setItemId(null);job.setBusinessType("BATCH_REGISTER");job.setBusinessId(20L);job.setPayloadJson("{\"productCode\":\"P-QA\"}");job.setPayloadHash(HashUtils.hashCanonicalTextHex(job.getPayloadJson()));intent.setBusinessType(job.getBusinessType());intent.setBusinessId(20L);intent.setBusinessIdHash(HashUtils.hashBusinessIdHex("PACK-QA"));intent.setEventName("BatchRegistered");intent.setPayloadHash(job.getPayloadHash());intent.setRequestHash(HashUtils.hashCanonicalTextPlainHex(job.getPayloadJson()));assertEquals("SUCCESS_MATCHED",preview().get("outcome"));assertEquals("registerBatch",expected.getMethod());assertEquals(4,expected.getArguments().size());}
    @Test void flowAndQualityExpectExactTypeAndTime(){for(String kind:List.of("CIRCULATION","QUALITY")){row.setKind(kind);row.setFrozenHash("b".repeat(64));CirculationEvidenceEntity flow=new CirculationEvidenceEntity();flow.setEventId(100L);when(circulation.find(7L)).thenReturn(flow);QualityEvidenceModels.Evidence qualityRow=new QualityEvidenceModels.Evidence();qualityRow.setEventId(100L);when(quality.find(7L)).thenReturn(qualityRow);TraceEventEntity event=new TraceEventEntity();event.setEventType("QUALITY".equals(kind)?"QUALITY_TEST":"LOGISTICS");event.setEventTime(LocalDateTime.of(2026,10,25,10,0));event.setCanonicalPayloadHash("c".repeat(64));when(events.findById(100L)).thenReturn(event);intent.setBusinessType("TRACE_EVENT_APPEND");intent.setBusinessId(100L);intent.setBusinessIdHash(HashUtils.hashBusinessIdHex("TRACE_EVENT:100"));intent.setPayloadHash("0x"+event.getCanonicalPayloadHash());intent.setRequestHash(row.getFrozenHash());intent.setEventName("TraceEventAppended");assertEquals("SUCCESS_MATCHED",preview().get("outcome"));assertEquals("appendTraceEvent",expected.getMethod());assertEquals("QUALITY".equals(kind)?"5":"8",expected.getArguments().get(2).getValue());}}
    @Test void nfcExpectationsPreserveBindingAndDisableIdentities(){for(String action:List.of("BIND","DISABLE")){row.setKind("NFC");PackingNfcOperation op=new PackingNfcOperation();op.setId(7L);op.setTagId(6L);op.setOperation(action);op.setPayloadHash("c".repeat(64));when(nfc.find(7L)).thenReturn(op);NfcTagEntity tag=new NfcTagEntity();tag.setUidHash("d".repeat(64));when(tags.findById(6L)).thenReturn(tag);intent.setBusinessType("PACKING_NFC_"+action);intent.setBusinessId(7L);intent.setBusinessIdHash("0x"+tag.getUidHash());intent.setPayloadHash("0x"+op.getPayloadHash());intent.setRequestHash(op.getPayloadHash());intent.setEventName("BIND".equals(action)?"NfcTagBound":"NfcStatusUpdated");assertEquals("SUCCESS_MATCHED",preview().get("outcome"));assertEquals("BIND".equals(action)?"bindNfcTag":"updateNfcStatus",expected.getMethod());}}
}
