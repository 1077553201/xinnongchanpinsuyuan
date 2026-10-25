package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.chain.*;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.*;
import org.nongchanpin.model.dto.SubmitPackingEvidenceRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.model.entity.PackingEvidenceModels.*;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.transaction.*;

class PackingEvidenceServiceTest {
    private final PackingEvidenceMapper evidence=mock(PackingEvidenceMapper.class);
    private final PackingTraceMapper traces=mock(PackingTraceMapper.class);
    private final ProductBatchMapper batches=mock(ProductBatchMapper.class);
    private final ProductMapper products=mock(ProductMapper.class);
    private final TraceItemMapper items=mock(TraceItemMapper.class);
    private final ChainService chain=mock(ChainService.class);
    private final ChainTransactionService transactions=mock(ChainTransactionService.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final PayloadService payloads=new PayloadService();
    private final ContractConfig config=new ContractConfig();
    private final PackingEvidenceService service=new PackingEvidenceService(evidence,traces,batches,products,items,new PackingManifestService(evidence),payloads,chain,transactions,config,mock(AuditService.class),manager);
    private final Map<Long,Snapshot> snapshots=new HashMap<>();
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private final Map<Long,ChainTransactionEntity> txs=new HashMap<>();
    private final Map<String,String> chainRecords=new HashMap<>();
    private final Actor actor=new Actor();
    private final Bridge bridge=new Bridge();
    private final ProductBatchEntity batch=new ProductBatchEntity();
    @BeforeEach void setup() {
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);config.setAgriTraceAddress("0xcontract");
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Source source=new Source();source.setId(9L);source.setCycleId(4L);source.setOrganizationId(3L);when(traces.source(9L)).thenReturn(source);
        bridge.setPackingId(9L);bridge.setBatchId(20L);bridge.setSourceJson("{\"plantedOn\":\"2026-10-01\",\"harvestedAt\":\"2026-10-10T12:00:00\"}");bridge.setSourceHash(HashUtils.hashCanonicalTextPlainHex(bridge.getSourceJson()));when(traces.bridge(9L)).thenReturn(bridge);
        batch.setId(20L);batch.setProductId(2L);batch.setBatchCode("PACK-QA");batch.setCreateTime(LocalDateTime.of(2026,10,11,0,0));batch.setStatus("ACTIVE");when(batches.findById(20L)).thenReturn(batch);
        ProductEntity product=new ProductEntity();product.setProductCode("PRODUCT-QA");when(products.findById(2L)).thenReturn(product);when(evidence.batchMetadata(any(),any())).thenReturn(1);
        TraceItemEntity item=new TraceItemEntity();item.setId(40L);item.setTraceCodePlain("CODE-QA");item.setTraceCodeHash(HashUtils.hashBusinessIdPlainHex("CODE-QA"));item.setTraceUrl("https://example.test/t/CODE-QA");item.setStatus("ACTIVE");when(items.findById(40L)).thenReturn(item);when(evidence.packingForItem(40L)).thenReturn(9L);
        when(evidence.snapshot(any())).thenAnswer(c->snapshots.get(c.getArgument(0)));
        doAnswer(c->{Snapshot s=c.getArgument(0);snapshots.put(s.getPackingId(),s);return 1;}).when(evidence).insertSnapshot(any());
        doAnswer(c->{Snapshot s=snapshots.get(c.getArgument(0));s.setLeaseToken(c.getArgument(1));s.setLeaseUntil(c.getArgument(2));return 1;}).when(evidence).claim(any(),any(),any());
        doAnswer(c->{Snapshot s=snapshots.get(c.getArgument(0));if(Objects.equals(s.getLeaseToken(),c.getArgument(1))){s.setLeaseToken(null);s.setLeaseUntil(null);}return 1;}).when(evidence).release(any(),any());
        when(evidence.job(any(),any())).thenAnswer(c->jobs.get(c.getArgument(0)+":"+c.getArgument(1)));
        doAnswer(c->{Job j=c.getArgument(0);j.setId((long)jobs.size()+1);jobs.put(j.getBusinessType()+":"+j.getBusinessId(),j);return 1;}).when(evidence).insertJob(any());
        when(evidence.jobs(9L)).thenAnswer(c->new ArrayList<>(jobs.values()));
        when(transactions.createPending(any(),any(),any(),any(),any(),any(),any())).thenAnswer(c->{ChainTransactionEntity t=new ChainTransactionEntity();t.setId((long)txs.size()+1);t.setChainStatus("PENDING");txs.put(t.getId(),t);return t;});
        when(transactions.get(any())).thenAnswer(c->txs.get(c.getArgument(0)));
        when(transactions.completeFromResult(any(),any())).thenAnswer(c->{ChainTransactionEntity t=c.getArgument(0);ChainTxResult r=c.getArgument(1);t.setChainStatus(r.getChainStatus().name());t.setReceiptStatus(r.getReceiptStatus());return t;});
        when(chain.getBatch(any())).thenAnswer(c->{ChainBatchView v=new ChainBatchView();v.setExists(chainRecords.containsKey("batch"));v.setPayloadHash(chainRecords.get("batch"));v.setProductIdHash(HashUtils.hashBusinessIdHex("PRODUCT-QA"));return v;});
        when(chain.getItem(any())).thenAnswer(c->{ChainItemView v=new ChainItemView();v.setExists(chainRecords.containsKey("item"));v.setPayloadHash(chainRecords.get("item"));v.setBatchIdHash(HashUtils.hashBusinessIdHex("PACK-QA"));return v;});
        when(chain.registerBatch(any())).thenAnswer(c->{RegisterBatchCommand command=c.getArgument(0);chainRecords.put("batch",HashUtils.hashCanonicalJsonHex(command.getPayload()));return receipt();});
        when(chain.bindItem(any())).thenAnswer(c->{BindItemCommand command=c.getArgument(0);chainRecords.put("item",HashUtils.hashCanonicalJsonHex(command.getPayload()));return receipt();});
    }
    @AfterEach void clear(){AccessContext.clear();}
    private ChainTxResult receipt(){ChainTxResult r=new ChainTxResult();r.setChainStatus(ChainStatus.CONFIRMED);r.setSuccess(true);r.setReceiptStatus("0");return r;}
    private SubmitPackingEvidenceRequest request(){SubmitPackingEvidenceRequest r=new SubmitPackingEvidenceRequest();r.setExpectedManifestHash((String)service.preview(9L).get("manifestHash"));r.setItemIds(List.of(40L));return r;}
    @Test void previewIsReadOnlyAndSubmissionPersistsIntentBeforeCallingChain() {
        SubmitPackingEvidenceRequest r=request();verifyNoInteractions(transactions);assertTrue(snapshots.isEmpty());
        doAnswer(c->{assertNotNull(snapshots.get(9L));assertEquals(2,jobs.size());assertNotNull(jobs.get("BATCH_REGISTER:20").getTxId());chainRecords.put("batch",HashUtils.hashCanonicalJsonHex(((RegisterBatchCommand)c.getArgument(0)).getPayload()));return receipt();}).when(chain).registerBatch(any());
        service.submit(9L,r);assertEquals("CONFIRMED",jobs.get("ITEM_BIND:40").getChainStatus());assertNull(snapshots.get(9L).getLeaseToken());
    }
    @Test void retryReadsLiveRecordsInsteadOfSendingDuplicateTransactions() {
        SubmitPackingEvidenceRequest r=request();service.submit(9L,r);service.submit(9L,r);verify(chain,times(1)).registerBatch(any());verify(chain,times(1)).bindItem(any());
    }
    @Test void stalePreviewAndForeignItemsAreRejectedBeforeAnyChainWrite() {
        SubmitPackingEvidenceRequest stale=request();stale.setExpectedManifestHash("wrong");assertThrows(IllegalArgumentException.class,()->service.submit(9L,stale));assertTrue(snapshots.isEmpty());
        SubmitPackingEvidenceRequest foreign=request();foreign.setItemIds(List.of(99L));assertThrows(IllegalArgumentException.class,()->service.submit(9L,foreign));verify(chain,never()).registerBatch(any());
    }
    @Test void viewerAndCrossOrganizationCannotSubmit() {
        SubmitPackingEvidenceRequest r=request();actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.submit(9L,r));actor.setRole("MANAGER");actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.preview(9L));verify(chain,never()).registerBatch(any());
    }
    @Test void unknownWriteIsNotBlindlySentAgainAndDownstreamIsSkipped() {
        doThrow(new IllegalStateException("timeout")).when(chain).registerBatch(any());SubmitPackingEvidenceRequest r=request();service.submit(9L,r);service.submit(9L,r);
        assertEquals("UNCERTAIN",jobs.get("BATCH_REGISTER:20").getChainStatus());verify(chain,times(1)).registerBatch(any());verify(chain,never()).bindItem(any());
    }
    @Test void laterLiveConfirmationRecoversUnknownWriteWithoutResending() {
        doThrow(new IllegalStateException("timeout")).when(chain).registerBatch(any());SubmitPackingEvidenceRequest r=request();service.submit(9L,r);chainRecords.put("batch",jobs.get("BATCH_REGISTER:20").getPayloadHash());service.submit(9L,r);
        assertEquals("CONFIRMED",jobs.get("ITEM_BIND:40").getChainStatus());verify(chain,times(1)).registerBatch(any());
    }
    @Test void mismatchingOnChainHashStopsRatherThanOverwritingIt() {
        chainRecords.put("batch","0xwrong");service.submit(9L,request());assertEquals("HASH_MISMATCH",jobs.get("BATCH_REGISTER:20").getErrorCode());verify(chain,never()).registerBatch(any());verify(chain,never()).bindItem(any());
    }
    @Test void chainDisabledIsPreservedAndDoesNotBindItems() {
        doReturn(ChainTxResult.disabled("0xcontract","disabled")).when(chain).registerBatch(any());service.submit(9L,request());assertEquals("CHAIN_DISABLED",jobs.get("BATCH_REGISTER:20").getChainStatus());verify(chain,never()).bindItem(any());
    }
    @Test void activeLeasePreventsOverlappingSubmission() {
        SubmitPackingEvidenceRequest r=request();service.submit(9L,r);snapshots.get(9L).setLeaseUntil(LocalDateTime.now().plusMinutes(1));assertThrows(IllegalArgumentException.class,()->service.submit(9L,r));verify(chain,times(1)).registerBatch(any());
    }
    @Test void manifestDamageCannotBePresentedAsTheOriginalFrozenVersion() {
        service.submit(9L,request());snapshots.get(9L).setManifestJson("{}");assertThrows(IllegalArgumentException.class,()->service.preview(9L));
    }
    @Test void frozenEvidenceDoesNotSilentlyAbsorbLaterRecords() {
        service.submit(9L,request());String hash=snapshots.get(9L).getManifestHash();clearInvocations(evidence);
        Map<String,Object> next=service.preview(9L);assertEquals(hash,next.get("manifestHash"));verify(evidence,never()).farming(any(),any(),any());verify(evidence,never()).environment(any(),any(),any());
    }
    @Test void definitiveFailedReceiptCanBeExplicitlyRetried() {
        ChainTxResult failed=new ChainTxResult();failed.setChainStatus(ChainStatus.FAILED);failed.setReceiptStatus("16");doReturn(failed).when(chain).registerBatch(any());
        SubmitPackingEvidenceRequest r=request();service.submit(9L,r);assertEquals("FAILED",jobs.get("BATCH_REGISTER:20").getChainStatus());
        doAnswer(c->{chainRecords.put("batch",HashUtils.hashCanonicalJsonHex(((RegisterBatchCommand)c.getArgument(0)).getPayload()));return receipt();}).when(chain).registerBatch(any());
        service.submit(9L,r);assertEquals("CONFIRMED",jobs.get("ITEM_BIND:40").getChainStatus());verify(chain,times(2)).registerBatch(any());
    }
}
