package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.chain.*;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.*;
import org.nongchanpin.model.dto.PackingNfcRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.transaction.*;

class PackingNfcServiceTest {
    private final PackingNfcMapper operations=mock(PackingNfcMapper.class);
    private final NfcTagMapper tags=mock(NfcTagMapper.class);
    private final TraceItemMapper items=mock(TraceItemMapper.class);
    private final PackingTraceMapper packing=mock(PackingTraceMapper.class);
    private final PackingEvidenceMapper production=mock(PackingEvidenceMapper.class);
    private final ProductBatchMapper batches=mock(ProductBatchMapper.class);
    private final PayloadService payloads=new PayloadService();
    private final ChainService chain=mock(ChainService.class);
    private final ChainTransactionService transactions=mock(ChainTransactionService.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final PackingNfcService service=new PackingNfcService(operations,tags,items,packing,production,batches,payloads,chain,transactions,mock(AuditService.class),new ContractConfig(),manager);
    private final Map<String,PackingNfcOperation> rows=new LinkedHashMap<>();
    private final Map<Long,ChainTransactionEntity> intents=new HashMap<>();
    private NfcTagEntity tag;
    private final TraceItemEntity item=new TraceItemEntity();
    private final ChainItemView chainItem=new ChainItemView();
    private final ChainNfcView chainTag=new ChainNfcView();
    private final Actor actor=new Actor();
    private final Source source=new Source();
    private final String uid="04:01:02:03:04:05:06";

    @BeforeEach void setup() {
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        source.setOrganizationId(3L);when(packing.source(9L)).thenReturn(source);
        Bridge bridge=new Bridge();bridge.setPackingId(9L);bridge.setBatchId(20L);bridge.setSourceJson("{\"packingCode\":\"PACK-QA\"}");bridge.setSourceHash(payloads.payloadHash(bridge.getSourceJson()));
        when(packing.publicBridge(40L)).thenReturn(bridge);
        PackingEvidenceModels.Snapshot saved=new PackingEvidenceModels.Snapshot();
        saved.setManifestJson(payloads.canonicalJson(Map.of("source",Map.of("sourceHash",bridge.getSourceHash()))));saved.setManifestHash(payloads.payloadHash(saved.getManifestJson()));
        saved.setBatchPayload(payloads.canonicalJson(Map.of("productCode","P-QA","metadata",Map.of("sourceHash",bridge.getSourceHash(),"evidenceHash",saved.getManifestHash()))));
        when(production.snapshot(9L)).thenReturn(saved);
        item.setId(40L);item.setBatchId(20L);item.setTraceCodePlain("CODE-QA");item.setTraceCodeHash(HashUtils.hashBusinessIdPlainHex("CODE-QA"));item.setTraceUrl("https://example.test/t/CODE-QA");item.setChainStatus("CONFIRMED");item.setStatus("ACTIVE");
        when(items.findById(40L)).thenReturn(item);
        ProductBatchEntity batch=new ProductBatchEntity();batch.setId(20L);batch.setBatchCode("PACK-QA");when(batches.findById(20L)).thenReturn(batch);
        ChainBatchView chainBatch=new ChainBatchView();chainBatch.setExists(true);chainBatch.setStatus(BigInteger.ONE);chainBatch.setProductIdHash(HashUtils.hashBusinessIdHex("P-QA"));chainBatch.setPayloadHash(HashUtils.hashCanonicalTextHex(saved.getBatchPayload()));when(chain.getBatch("PACK-QA")).thenReturn(chainBatch);
        chainItem.setExists(true);chainItem.setStatus(BigInteger.ONE);chainItem.setBatchIdHash(HashUtils.hashBusinessIdHex("PACK-QA"));chainItem.setPayloadHash(payloads.payloadHashWithPrefix(Map.of("traceCodeHash",item.getTraceCodeHash(),"batchCode","PACK-QA","traceUrl",item.getTraceUrl())));chainItem.setNfcUidHash("0x"+"0".repeat(64));
        when(chain.getItem("CODE-QA")).thenReturn(chainItem);when(chain.getNfcTag(anyString())).thenReturn(chainTag);
        when(operations.operation(eq(40L),anyString())).thenAnswer(c->rows.get(c.getArgument(1)));
        when(operations.history(40L)).thenAnswer(c->new ArrayList<>(rows.values()));
        doAnswer(c->{PackingNfcOperation row=c.getArgument(0);row.setId((long)rows.size()+1);row.setRecordedAt(LocalDateTime.now());rows.put(row.getOperation(),row);return 1;}).when(operations).insert(any());
        doAnswer(c->{rows.get("BIND").setLeaseToken(c.getArgument(1));rows.get("BIND").setLeaseUntil(c.getArgument(2));return 1;}).when(operations).claim(any(),any(),any());
        doAnswer(c->{PackingNfcOperation row=rows.get("BIND");if(Objects.equals(row.getLeaseToken(),c.getArgument(1))){row.setLeaseToken(null);row.setLeaseUntil(null);}return 1;}).when(operations).release(any(),any());
        doAnswer(c->{tag=c.getArgument(0);tag.setId(7L);return 1;}).when(tags).insert(any());
        when(tags.findById(7L)).thenAnswer(c->tag);when(tags.findLatestByItemId(40L)).thenAnswer(c->tag);
        when(tags.findByUidHash(anyString())).thenAnswer(c->tag!=null&&tag.getUidHash().equals(c.getArgument(0))?tag:null);
        doAnswer(c->{tag.setBindStatus(c.getArgument(1));tag.setStatus(c.getArgument(2));return 1;}).when(tags).updateStatus(any(),any(),any());
        when(transactions.createPending(any(),any(),any(),any(),any(),any(),any())).thenAnswer(c->{
            assertTrue(rows.containsKey("BIND"));ChainTransactionEntity intent=new ChainTransactionEntity();intent.setId((long)intents.size()+1);intent.setChainStatus("PENDING");intents.put(intent.getId(),intent);return intent;
        });
        when(transactions.get(any())).thenAnswer(c->intents.get(c.getArgument(0)));
        when(transactions.completeFromResult(any(),any())).thenAnswer(c->{ChainTransactionEntity intent=c.getArgument(0);ChainTxResult result=c.getArgument(1);intent.setChainStatus(result.getChainStatus().name());intent.setReceiptStatus(result.getReceiptStatus());return intent;});
        when(chain.bindNfcTag(any())).thenAnswer(c->{if(c.getArgument(0)==null)return null;assertNotNull(rows.get("BIND").getTxId());bindOnChain(c.getArgument(0));return receipt(ChainStatus.CONFIRMED,"0");});
        when(chain.updateNfcStatus(any())).thenAnswer(c->{UpdateNfcStatusCommand command=c.getArgument(0);if(command==null)return null;assertNotNull(rows.get("DISABLE").getTxId());chainTag.setStatus(BigInteger.valueOf(3));chainTag.setReasonHash(payloads.payloadHashWithPrefix(command.getReasonPayload()));return receipt(ChainStatus.CONFIRMED,"0");});
    }
    @AfterEach void clear(){AccessContext.clear();}
    private void bindOnChain(BindNfcTagCommand command){chainTag.setExists(true);chainTag.setNfcUidHash(command.getNfcUid());chainTag.setItemCodeHash(HashUtils.hashBusinessIdHex(command.getItemCode()));chainTag.setPayloadHash(payloads.payloadHashWithPrefix(command.getPayload()));chainTag.setStatus(BigInteger.ONE);chainItem.setNfcUidHash(command.getNfcUid());}
    private ChainTxResult receipt(ChainStatus state,String status){ChainTxResult value=new ChainTxResult();value.setChainStatus(state);value.setReceiptStatus(status);return value;}
    private PackingNfcRequest request(String action){PackingNfcRequest r=new PackingNfcRequest();r.setOperation(action);r.setUid("BIND".equals(action)?uid:null);r.setReason("DISABLE".equals(action)?"PRIVATE-QA 标签损坏":null);r.setExpectedHash((String)service.detail(40L,action,r.getUid(),r.getReason()).get("payloadHash"));return r;}
    private void bind(){service.submit(40L,request("BIND"));}

    @Test void normalizesRealUidFormats(){assertEquals(PackingNfcService.uidHash(uid),PackingNfcService.uidHash("04 01 02 03 04 05 06"));assertEquals(PackingNfcService.uidHash("aabbccdd"),PackingNfcService.uidHash("AA-BB-CC-DD"));}
    @Test void rejectsMalformedOrUnsupportedUid(){for(String value:List.of("","123456","tag-001","0x04010203040506","GG112233"))assertThrows(IllegalArgumentException.class,()->PackingNfcService.uidHash(value));}
    @Test void previewHasNoWritesOrNetwork(){Map<String,Object> p=service.detail(40L,"BIND",uid,null);assertNotNull(p.get("payloadHash"));assertTrue(rows.isEmpty());verifyNoInteractions(chain,transactions);verify(tags,never()).insert(any());}
    @Test void previewRejectsUnmappedLegacyItem(){assertThrows(IllegalArgumentException.class,()->service.detail(41L,null,null,null));verifyNoInteractions(chain);}
    @Test void organizationIsCheckedBeforeReadingIdentity(){actor.setOrganizationId(4L);assertThrows(AccessException.class,()->service.detail(40L,null,null,null));verifyNoInteractions(chain);}
    @Test void viewerCannotSubmit(){PackingNfcRequest r=request("BIND");actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.submit(40L,r));verifyNoInteractions(chain,transactions);}
    @Test void productionMustBeConfirmedBeforeBinding(){PackingNfcRequest r=request("BIND");item.setChainStatus("NOT_SUBMITTED");assertThrows(IllegalArgumentException.class,()->service.submit(40L,r));assertTrue(rows.isEmpty());}
    @Test void missingProductionSnapshotCannotBePreviewed(){when(production.snapshot(9L)).thenReturn(null);assertThrows(IllegalArgumentException.class,()->request("BIND"));}
    @Test void staleConfirmationCannotCreateBinding(){PackingNfcRequest r=request("BIND");r.setExpectedHash("0".repeat(64));assertThrows(IllegalArgumentException.class,()->service.submit(40L,r));assertTrue(rows.isEmpty());assertNull(tag);}
    @Test void intentPrecedesRpcAndIdentityIsLiveVerified(){bind();assertEquals("CONFIRMED",rows.get("BIND").getChainStatus());assertEquals("BOUND:CONFIRMED",tag.getBindStatus());assertEquals(true,service.publicState(40L).get("active"));assertEquals("VERIFIED",service.publicState(40L).get("verificationStatus"));verify(items,never()).updateChainStatus(any(),any());}
    @Test void repeatedBindDoesNotSendAnotherTransaction(){PackingNfcRequest r=request("BIND");service.submit(40L,r);service.submit(40L,r);verify(chain,times(1)).bindNfcTag(any());assertEquals(1,intents.size());assertEquals(1,rows.size());}
    @Test void existingBindingCannotChangeUid(){bind();assertThrows(IllegalArgumentException.class,()->service.detail(40L,"BIND","11223344",null));verify(chain,times(1)).bindNfcTag(any());}
    @Test void uidUsedByOtherItemCannotBeReassigned(){PackingNfcRequest r=request("BIND");NfcTagEntity other=new NfcTagEntity();other.setItemId(41L);when(tags.findByUidHash(anyString())).thenReturn(other);assertThrows(IllegalArgumentException.class,()->service.submit(40L,r));assertTrue(rows.isEmpty());verifyNoInteractions(chain);}
    @Test void leaseStopsConcurrentSubmission(){bind();rows.get("BIND").setLeaseUntil(LocalDateTime.now().plusMinutes(2));assertThrows(IllegalArgumentException.class,()->service.submit(40L,request("BIND")));verify(chain,times(1)).bindNfcTag(any());}
    @Test void unavailableReadDoesNotSubmit(){PackingNfcRequest r=request("BIND");when(chain.getItem(anyString())).thenThrow(new IllegalStateException());service.submit(40L,r);assertEquals("NOT_SUBMITTED",rows.get("BIND").getChainStatus());assertTrue(intents.isEmpty());verify(chain,never()).bindNfcTag(any());}
    @Test void sourceMismatchStopsWrite(){PackingNfcRequest r=request("BIND");chainItem.setPayloadHash("0x"+"f".repeat(64));service.submit(40L,r);assertEquals("SOURCE_MISMATCH",rows.get("BIND").getErrorCode());verify(chain,never()).bindNfcTag(any());}
    @Test void anotherOnChainTagIsNotOverwritten(){PackingNfcRequest r=request("BIND");chainItem.setNfcUidHash("0x"+"f".repeat(64));service.submit(40L,r);assertEquals("OTHER_TAG_BOUND",rows.get("BIND").getErrorCode());verify(chain,never()).bindNfcTag(any());}
    @Test void recalledSourceBlocksNewBinding(){PackingNfcRequest r=request("BIND");chainItem.setStatus(BigInteger.valueOf(4));service.submit(40L,r);assertEquals("SOURCE_NOT_ACTIVE",rows.get("BIND").getErrorCode());verify(chain,never()).bindNfcTag(any());}
    @Test void unknownResultIsNeverBlindlyRetried(){PackingNfcRequest r=request("BIND");when(chain.bindNfcTag(any())).thenThrow(new IllegalStateException());service.submit(40L,r);service.submit(40L,r);assertEquals("UNCERTAIN",rows.get("BIND").getChainStatus());assertEquals("RECONCILE_REQUIRED",rows.get("BIND").getErrorCode());verify(chain,times(1)).bindNfcTag(any());}
    @Test void lostResponseCanRecoverFromLiveIdentity(){PackingNfcRequest r=request("BIND");when(chain.bindNfcTag(any())).thenAnswer(c->{bindOnChain(c.getArgument(0));throw new IllegalStateException();});service.submit(40L,r);assertEquals("UNCERTAIN",rows.get("BIND").getChainStatus());service.submit(40L,r);assertEquals("CONFIRMED",rows.get("BIND").getChainStatus());verify(chain,times(1)).bindNfcTag(any());}
    @Test void disabledChainCanBeExplicitlyRetried(){PackingNfcRequest r=request("BIND");when(chain.bindNfcTag(any())).thenReturn(receipt(ChainStatus.CHAIN_DISABLED,null));service.submit(40L,r);when(chain.bindNfcTag(any())).thenAnswer(c->{bindOnChain(c.getArgument(0));return receipt(ChainStatus.CONFIRMED,"0");});service.submit(40L,r);assertEquals("CONFIRMED",rows.get("BIND").getChainStatus());assertEquals(2,intents.size());}
    @Test void knownFailureCanBeExplicitlyRetried(){PackingNfcRequest r=request("BIND");when(chain.bindNfcTag(any())).thenReturn(receipt(ChainStatus.FAILED,"1"));service.submit(40L,r);when(chain.bindNfcTag(any())).thenAnswer(c->{bindOnChain(c.getArgument(0));return receipt(ChainStatus.CONFIRMED,"0");});service.submit(40L,r);assertEquals("CONFIRMED",rows.get("BIND").getChainStatus());}
    @Test void tamperedFrozenPayloadStopsRecovery(){bind();rows.get("BIND").setPayloadHash("0".repeat(64));assertThrows(IllegalArgumentException.class,()->service.detail(40L,null,null,null));verify(chain,times(1)).bindNfcTag(any());}
    @Test void publicVerificationDoesNotTrustCachedSuccess(){bind();chainTag.setPayloadHash("0x"+"f".repeat(64));assertEquals("NOT_VERIFIED",service.publicState(40L).get("verificationStatus"));assertEquals(false,service.publicState(40L).get("active"));}
    @Test void publicIdentityDoesNotLeakToOtherCodes(){bind();assertNull(service.publicState(41L));assertThrows(AccessException.class,()->service.resolve("a".repeat(64)));assertThrows(AccessException.class,()->service.resolve("invalid"));assertEquals("CODE-QA",service.resolve(tag.getUidHash()).get("traceCode"));}
    @Test void disableRequiresConfirmedOriginalBinding(){PackingNfcRequest r=request("BIND");when(chain.bindNfcTag(any())).thenThrow(new IllegalStateException());service.submit(40L,r);assertThrows(IllegalArgumentException.class,()->service.submit(40L,request("DISABLE")));assertFalse(rows.containsKey("DISABLE"));}
    @Test void disableIsAuditedAndNotResent(){bind();PackingNfcRequest r=request("DISABLE");service.submit(40L,r);service.submit(40L,r);assertEquals(2,rows.size());assertEquals("DISABLED",tag.getStatus());Map<String,Object> value=service.publicState(40L);assertEquals("VERIFIED",value.get("verificationStatus"));assertEquals(false,value.get("active"));assertEquals(true,value.get("disableRequested"));assertFalse(payloads.canonicalJson(value).contains("PRIVATE-QA"));verify(chain,times(1)).updateNfcStatus(any());}
    @Test void pendingDisableImmediatelyStopsAutomaticResolution(){bind();when(chain.updateNfcStatus(any())).thenThrow(new IllegalStateException());service.submit(40L,request("DISABLE"));Map<String,Object> value=service.publicState(40L);assertEquals(false,value.get("active"));assertEquals(true,value.get("disableRequested"));assertEquals("NOT_VERIFIED",value.get("verificationStatus"));}
    @Test void disableReasonCannotBeSilentlyChanged(){bind();service.submit(40L,request("DISABLE"));assertThrows(IllegalArgumentException.class,()->service.detail(40L,"DISABLE",null,"new reason"));}
    @Test void failedPublicReadCannotAutoResolve(){bind();when(chain.getNfcTag(anyString())).thenThrow(new IllegalStateException());Map<String,Object> value=service.publicState(40L);assertEquals(false,value.get("active"));assertEquals("UNAVAILABLE",value.get("verificationSource"));}
}
