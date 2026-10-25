package org.nongchanpin.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

/** One immutable UID binding per complete package. No replacement or physical tag authentication. */
@Service
public class PackingNfcService {
    private final PackingNfcMapper operations;
    private final NfcTagMapper tags;
    private final TraceItemMapper items;
    private final PackingTraceMapper packing;
    private final PackingEvidenceMapper production;
    private final ProductBatchMapper batches;
    private final PayloadService payloads;
    private final ChainService chain;
    private final ChainTransactionService transactions;
    private final AuditService audit;
    private final ContractConfig config;
    private final TransactionTemplate tx;

    public PackingNfcService(PackingNfcMapper operations,NfcTagMapper tags,TraceItemMapper items,
            PackingTraceMapper packing,PackingEvidenceMapper production,ProductBatchMapper batches,
            PayloadService payloads,ChainService chain,ChainTransactionService transactions,
            AuditService audit,ContractConfig config,PlatformTransactionManager manager) {
        this.operations=operations;this.tags=tags;this.items=items;this.packing=packing;
        this.production=production;this.batches=batches;this.payloads=payloads;this.chain=chain;
        this.transactions=transactions;this.audit=audit;this.config=config;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long itemId,String operation,String uid,String reason) {
        Bridge bridge=access(itemId);
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("itemId",itemId);value.put("packingId",bridge.getPackingId());
        value.put("traceCode",items.findById(itemId).getTraceCodePlain());
        List<PackingNfcOperation> history=operations.history(itemId);
        for(PackingNfcOperation row:history)validate(row);
        List<Map<String,Object>> summaries=new ArrayList<>();
        for(PackingNfcOperation row:history) {
            Map<String,Object> summary=new LinkedHashMap<>();summary.put("operation",row.getOperation());
            summary.put("payloadHash",row.getPayloadHash());summary.put("chainStatus",row.getChainStatus());
            summary.put("txId",row.getTxId());summary.put("errorCode",row.getErrorCode());
            summary.put("recordedAt",row.getRecordedAt());summary.put("privateReason",row.getPrivateReason());summaries.add(summary);
        }
        value.put("history",summaries);
        PackingNfcOperation bind=operations.operation(itemId,"BIND");
        if(bind!=null)value.put("uidHash",tags.findById(bind.getTagId()).getUidHash());
        if(operation!=null&&!operation.isBlank()) {
            Map<String,Object> candidate=candidate(itemId,operation,uid,reason);
            value.put("payload",candidate);value.put("payloadHash",payloads.requestHash(candidate));
            value.put("operation",operation);
        }
        return value;
    }

    public Map<String,Object> submit(Long itemId,PackingNfcRequest request) {
        AccessContext.write();access(itemId);
        String token=UUID.randomUUID().toString();
        PackingNfcOperation row=tx.execute(s->prepare(itemId,request,token));
        try { run(row,token); }
        finally { tx.execute(s->{operations.release(itemId,token);return null;}); }
        return detail(itemId,null,null,null);
    }

    public void reconcileFrozen(Long id,String expectedHash,Long expectedTx) {
        AccessContext.write();String token=UUID.randomUUID().toString();
        PackingNfcOperation row=tx.execute(s->{
            PackingNfcOperation observed=operations.find(id);if(observed==null)throw new IllegalArgumentException("NFC 操作不存在。");
            Bridge bridge=access(observed.getItemId());packing.lock(bridge.getPackingId());
            PackingNfcOperation saved=operations.lockFrozen(id);validate(saved);
            ReconciliationGuard.check(saved.getPayloadHash(),saved.getTxId(),expectedHash,expectedTx);
            PackingNfcOperation bind=operations.operation(saved.getItemId(),"BIND");
            if(bind.getLeaseUntil()!=null&&bind.getLeaseUntil().isAfter(LocalDateTime.now()))throw new IllegalArgumentException("原操作仍在处理中。");
            operations.claim(saved.getItemId(),token,LocalDateTime.now().plusMinutes(5));return saved;
        });
        try{run(row,token,false);}finally{tx.execute(s->{operations.release(row.getItemId(),token);return null;});}
    }
    private PackingNfcOperation prepare(Long itemId,PackingNfcRequest request,String token) {
        Bridge bridge=access(itemId);packing.lock(bridge.getPackingId());
        PackingNfcOperation bind=operations.operation(itemId,"BIND");
        if(bind!=null&&bind.getLeaseUntil()!=null&&bind.getLeaseUntil().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("此码已有 NFC 操作处理中，请稍后刷新回执。");
        Map<String,Object> candidate=candidate(itemId,request.getOperation(),request.getUid(),request.getReason());
        String json=payloads.canonicalJson(candidate),hash=payloads.payloadHash(json);
        if(!hash.equals(request.getExpectedHash()))throw new IllegalArgumentException("确认摘要已变化，请刷新后重新核对。");
        PackingNfcOperation row=operations.operation(itemId,request.getOperation());
        if(row==null) {
            if("BIND".equals(request.getOperation())) {
                TraceItemEntity item=items.findById(itemId);
                if(!"CONFIRMED".equals(item.getChainStatus())||!"ACTIVE".equals(item.getStatus()))
                    throw new IllegalArgumentException("请先完成本码生产来源存证，并确认码仍有效。");
                if(tags.findLatestByItemId(itemId)!=null)throw new IllegalArgumentException("本码已有标签历史，不能覆盖或换绑。");
                String uidHash=candidate.get("uidHash").toString();
                if(tags.findByUidHash(uidHash)!=null)throw new IllegalArgumentException("标签已使用，不能关联其他包装。");
                NfcTagEntity tag=new NfcTagEntity();tag.setItemId(itemId);tag.setUidHash(uidHash);
                tag.setBindStatus("BINDING");tag.setBoundAt(LocalDateTime.now());tag.setStatus("ACTIVE");
                tags.insert(tag);
                row=operation(bridge,itemId,tag.getId(),"BIND",json,hash,null);
            } else {
                if(bind==null||!"CONFIRMED".equals(bind.getChainStatus()))
                    throw new IllegalArgumentException("请先恢复并确认原绑定回执，再办理停用。");
                row=operation(bridge,itemId,bind.getTagId(),"DISABLE",json,hash,request.getReason().trim());
            }
            operations.insert(row);
            audit.record("PACKING_NFC_FREEZE","TRACE_ITEM",itemId,row.getOperation()+" / "+hash);
        }
        validate(row);
        operations.claim(itemId,token,LocalDateTime.now().plusMinutes(5));
        updateBusiness(row);
        return row;
    }

    private PackingNfcOperation operation(Bridge bridge,Long itemId,Long tagId,String action,String json,String hash,String reason) {
        PackingNfcOperation row=new PackingNfcOperation();row.setPackingId(bridge.getPackingId());
        row.setItemId(itemId);row.setTagId(tagId);row.setOperation(action);row.setPayloadJson(json);
        row.setPayloadHash(hash);row.setPrivateReason(reason);row.setChainStatus("NOT_SUBMITTED");
        row.setRecordedBy(AccessContext.require().getId());return row;
    }

    private Map<String,Object> candidate(Long itemId,String action,String uid,String reason) {
        if(!"BIND".equals(action)&&!"DISABLE".equals(action))throw new IllegalArgumentException("NFC 操作类型无效。");
        Bridge bridge=access(itemId);PackingEvidenceModels.Snapshot saved=snapshot(bridge);
        PackingNfcOperation existing=operations.operation(itemId,action),bind=operations.operation(itemId,"BIND");
        if(existing!=null) {
            validate(existing);
            if("BIND".equals(action)&&uid!=null&&!uid.isBlank()&&!tags.findById(existing.getTagId()).getUidHash().equals(uidHash(uid)))
                throw new IllegalArgumentException("本码已冻结其他标签，不能覆盖原绑定。");
            if("DISABLE".equals(action)&&reason!=null&&!reason.isBlank()&&!reason.trim().equals(existing.getPrivateReason()))
                throw new IllegalArgumentException("已冻结停用原因，恢复时不能改写原确认。");
            return map(existing.getPayloadJson());
        }
        Map<String,Object> value=payloads.map();value.put("schema","PACKING_NFC_V1");
        value.put("operation",action);value.put("traceCodeHash",items.findById(itemId).getTraceCodeHash());
        value.put("sourceHash",bridge.getSourceHash());value.put("productionManifestHash",saved.getManifestHash());
        if("BIND".equals(action))value.put("uidHash",uidHash(uid));
        else {
            if(bind==null)throw new IllegalArgumentException("本码尚未登记 NFC 绑定。");
            validate(bind);
            if(reason==null||reason.trim().isEmpty()||reason.length()>500)throw new IllegalArgumentException("请填写不超过 500 字的停用原因。");
            value.put("uidHash",tags.findById(bind.getTagId()).getUidHash());
            value.put("bindPayloadHash",bind.getPayloadHash());
            value.put("reasonDigest",payloads.requestHash(Map.of("reason",reason.trim())));
        }
        return value;
    }

    public static String uidHash(String uid) {
        String normalized=uid==null?"":uid.replaceAll("[\\s:-]","").toUpperCase(Locale.ROOT);
        if(!normalized.matches("(?:[0-9A-F]{8}|[0-9A-F]{14}|[0-9A-F]{20})"))
            throw new IllegalArgumentException("请输入 4、7 或 10 字节十六进制 UID，可带空格、冒号或短横线。");
        return HashUtils.hashBusinessIdPlainHex(normalized);
    }

    private void run(PackingNfcOperation row,String token) { run(row,token,true); }
    private void run(PackingNfcOperation row,String token,boolean allowSend) {
        tx.execute(s->{locked(row,token);return null;});validate(row);
        NfcTagEntity tag=tags.findById(row.getTagId());
        TraceItemEntity item=items.findById(row.getItemId());
        try {
            ChainItemView actualItem=chain.getItem(item.getTraceCodePlain());
            ChainNfcView actual=chain.getNfcTag("0x"+tag.getUidHash());
            if(actualItem==null||actual==null)throw new IllegalStateException();
            if(!sourceMatches(item,actualItem)){state(row,token,"FAILED","SOURCE_MISMATCH");return;}
            PackingNfcOperation bind=operations.operation(row.getItemId(),"BIND");
            if(actual.isExists()) {
                if(!bindingMatches(item,tag,bind,actualItem,actual)){state(row,token,"FAILED","HASH_MISMATCH");return;}
                if("BIND".equals(row.getOperation())){state(row,token,"CONFIRMED",null);return;}
                if(BigInteger.valueOf(3).equals(actual.getStatus())) {
                    if(!("0x"+row.getPayloadHash()).equalsIgnoreCase(actual.getReasonHash())){state(row,token,"FAILED","HASH_MISMATCH");return;}
                    state(row,token,"CONFIRMED",null);return;
                }
                if(!BigInteger.ONE.equals(actual.getStatus())){state(row,token,"FAILED","SOURCE_NOT_ACTIVE");return;}
            } else {
                if("DISABLE".equals(row.getOperation())){state(row,token,"UNCERTAIN","CHAIN_RECORD_MISSING");return;}
                if(!emptyHash(actualItem.getNfcUidHash())){state(row,token,"FAILED","OTHER_TAG_BOUND");return;}
                if(actualItem.getStatus()==null||actualItem.getStatus().intValue()<1||actualItem.getStatus().intValue()>3) {
                    state(row,token,"FAILED","SOURCE_NOT_ACTIVE");return;
                }
                ProductBatchEntity batch=batches.findById(item.getBatchId());
                if(!BigInteger.ONE.equals(chain.getBatch(batch.getBatchCode()).getStatus())){state(row,token,"FAILED","SOURCE_NOT_ACTIVE");return;}
            }
        } catch(RuntimeException error) {
            state(row,token,row.getTxId()==null?"NOT_SUBMITTED":"UNCERTAIN","CHAIN_READ_UNAVAILABLE");return;
        }
        if(row.getTxId()!=null) {
            ChainTransactionEntity previous=transactions.get(row.getTxId());
            boolean retry="CHAIN_DISABLED".equals(previous.getChainStatus())||("FAILED".equals(previous.getChainStatus())&&previous.getReceiptStatus()!=null&&!previous.getReceiptStatus().isBlank());
            if(!retry){state(row,token,"UNCERTAIN","RECONCILE_REQUIRED");return;}
        }
        if("CONFIRMED".equals(row.getChainStatus())){state(row,token,"UNCERTAIN","CHAIN_RECORD_MISSING");return;}
        if(!allowSend){state(row,token,"UNCERTAIN","RECONCILE_REQUIRED");return;}
        ChainTransactionEntity pending=tx.execute(s->{
            locked(row,token);
            ChainTransactionEntity intent=transactions.createPending("PACKING_NFC_"+row.getOperation(),row.getId(),
                    "0x"+tag.getUidHash(),config.getAgriTraceAddress(),"BIND".equals(row.getOperation())?"NfcTagBound":"NfcStatusUpdated",
                    "0x"+row.getPayloadHash(),row.getPayloadHash());
            row.setTxId(intent.getId());row.setChainStatus("PENDING");row.setErrorCode(null);
            operations.state(row);updateBusiness(row);return intent;
        });
        ChainTxResult result;
        try {
            if("BIND".equals(row.getOperation())) {
                BindNfcTagCommand command=new BindNfcTagCommand();command.setItemCode(item.getTraceCodePlain());
                command.setNfcUid("0x"+tag.getUidHash());command.setPayload(map(row.getPayloadJson()));
                result=chain.bindNfcTag(command);
            } else {
                UpdateNfcStatusCommand command=new UpdateNfcStatusCommand();command.setNfcUid("0x"+tag.getUidHash());
                command.setStatus(3);command.setReasonPayload(map(row.getPayloadJson()));result=chain.updateNfcStatus(command);
            }
        } catch(RuntimeException error){state(row,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");return;}
        if(result==null||result.getChainStatus()==null){state(row,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");return;}
        final ChainTxResult receipt=result;
        tx.execute(s->{locked(row,token);transactions.completeFromResult(pending,receipt);return null;});
        if(result.getChainStatus()==ChainStatus.CONFIRMED){run(row,token,allowSend);return;}
        if(result.getChainStatus()==ChainStatus.CHAIN_DISABLED){state(row,token,"CHAIN_DISABLED","CHAIN_DISABLED");return;}
        boolean known=result.getReceiptStatus()!=null&&!result.getReceiptStatus().isBlank();
        state(row,token,known?"FAILED":"UNCERTAIN",known?"TRANSACTION_FAILED":"CHAIN_RESULT_UNKNOWN");
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicState(Long itemId) {
        PackingNfcOperation bind=operations.operation(itemId,"BIND");if(bind==null)return null;
        validate(bind);NfcTagEntity tag=tags.findById(bind.getTagId());TraceItemEntity item=items.findById(itemId);
        PackingNfcOperation disable=operations.operation(itemId,"DISABLE");if(disable!=null)validate(disable);
        Map<String,Object> value=new LinkedHashMap<>();value.put("uidHash",tag.getUidHash());
        value.put("traceCode",item.getTraceCodePlain());value.put("bindingChainStatus",bind.getChainStatus());
        value.put("bindingHash",bind.getPayloadHash());value.put("recordedAt",bind.getRecordedAt());
        value.put("disableRequested",disable!=null);value.put("active",false);value.put("verificationStatus","NOT_VERIFIED");
        if(disable!=null){value.put("disableChainStatus",disable.getChainStatus());value.put("disableHash",disable.getPayloadHash());value.put("disableRecordedAt",disable.getRecordedAt());}
        try {
            ChainItemView actualItem=chain.getItem(item.getTraceCodePlain());ChainNfcView actual=chain.getNfcTag("0x"+tag.getUidHash());
            boolean bound=actualItem!=null&&actual!=null&&sourceMatches(item,actualItem)&&bindingMatches(item,tag,bind,actualItem,actual);
            boolean verified=bound&&(disable==null||BigInteger.valueOf(3).equals(actual.getStatus())&&("0x"+disable.getPayloadHash()).equalsIgnoreCase(actual.getReasonHash()));
            value.put("verificationStatus",verified?"VERIFIED":"NOT_VERIFIED");
            value.put("active",bound&&disable==null&&BigInteger.ONE.equals(actual.getStatus()));
        } catch(RuntimeException error){value.put("verificationSource","UNAVAILABLE");return value;}
        value.put("verificationSource","LIVE_CHAIN");return value;
    }

    public Map<String,Object> resolve(String uidHash) {
        if(uidHash==null||!uidHash.matches("[0-9a-f]{64}"))throw new AccessException(404,"标签查询身份不存在。");
        NfcTagEntity tag=tags.findByUidHash(uidHash);
        PackingNfcOperation bind=tag==null?null:operations.operation(tag.getItemId(),"BIND");
        if(bind==null||!tag.getId().equals(bind.getTagId()))throw new AccessException(404,"标签查询身份不存在。");
        return publicState(tag.getItemId());
    }

    private boolean sourceMatches(TraceItemEntity item,ChainItemView actual) {
        Bridge bridge=packing.publicBridge(item.getId());PackingEvidenceModels.Snapshot saved=snapshot(bridge);
        ProductBatchEntity batch=batches.findById(item.getBatchId());
        ChainBatchView chainBatch=chain.getBatch(batch.getBatchCode());
        Map<String,Object> body=map(saved.getBatchPayload());
        return actual.isExists()&&chainBatch!=null&&chainBatch.isExists()
                &&HashUtils.hashBusinessIdHex(batch.getBatchCode()).equalsIgnoreCase(actual.getBatchIdHash())
                &&HashUtils.hashBusinessIdHex(body.get("productCode").toString()).equalsIgnoreCase(chainBatch.getProductIdHash())
                &&HashUtils.hashCanonicalTextHex(saved.getBatchPayload()).equalsIgnoreCase(chainBatch.getPayloadHash())
                &&payloads.payloadHashWithPrefix(Map.of("traceCodeHash",item.getTraceCodeHash(),"batchCode",batch.getBatchCode(),"traceUrl",item.getTraceUrl())).equalsIgnoreCase(actual.getPayloadHash());
    }

    private boolean bindingMatches(TraceItemEntity item,NfcTagEntity tag,PackingNfcOperation bind,ChainItemView actualItem,ChainNfcView actual) {
        return actual.isExists()&&actual.getStatus()!=null&&actual.getStatus().intValue()>=1&&actual.getStatus().intValue()<=3
                &&("0x"+tag.getUidHash()).equalsIgnoreCase(actual.getNfcUidHash())
                &&("0x"+tag.getUidHash()).equalsIgnoreCase(actualItem.getNfcUidHash())
                &&HashUtils.hashBusinessIdHex(item.getTraceCodePlain()).equalsIgnoreCase(actual.getItemCodeHash())
                &&("0x"+bind.getPayloadHash()).equalsIgnoreCase(actual.getPayloadHash());
    }

    private Bridge access(Long itemId) {
        Bridge bridge=packing.publicBridge(itemId);
        if(bridge==null)throw new IllegalArgumentException("此码没有明确的包装来源，不能使用本入口。");
        Source source=packing.source(bridge.getPackingId());
        if(source==null)throw new IllegalArgumentException("包装来源不存在。");
        AccessContext.checkOrganization(source.getOrganizationId());return bridge;
    }
    private PackingEvidenceModels.Snapshot snapshot(Bridge bridge) {
        PackingEvidenceModels.Snapshot saved=bridge==null?null:production.snapshot(bridge.getPackingId());
        if(saved==null)throw new IllegalArgumentException("请先冻结并核对生产来源证据。");
        PackingEvidenceService.validate(saved,bridge);return saved;
    }
    void validate(PackingNfcOperation row) {
        Bridge bridge=packing.publicBridge(row.getItemId());PackingEvidenceModels.Snapshot saved=snapshot(bridge);
        NfcTagEntity tag=tags.findById(row.getTagId());TraceItemEntity item=items.findById(row.getItemId());
        Map<String,Object> body=map(row.getPayloadJson());
        boolean match=bridge.getPackingId().equals(row.getPackingId())&&tag!=null&&item!=null
                &&row.getItemId().equals(tag.getItemId())&&tag.getUidHash().equals(body.get("uidHash"))
                &&"PACKING_NFC_V1".equals(body.get("schema"))&&row.getOperation().equals(body.get("operation"))
                &&item.getTraceCodeHash().equals(body.get("traceCodeHash"))&&bridge.getSourceHash().equals(body.get("sourceHash"))
                &&saved.getManifestHash().equals(body.get("productionManifestHash"))
                &&payloads.payloadHash(row.getPayloadJson()).equals(row.getPayloadHash());
        if("DISABLE".equals(row.getOperation())) {
            PackingNfcOperation bind=operations.operation(row.getItemId(),"BIND");
            match=match&&bind!=null&&bind.getTagId().equals(row.getTagId())&&bind.getPayloadHash().equals(body.get("bindPayloadHash"))
                    &&payloads.requestHash(Map.of("reason",row.getPrivateReason())).equals(body.get("reasonDigest"));
        }
        if(!match)throw new IllegalArgumentException("NFC 历史摘要或来源身份不一致，请核对原记录。");
    }
    private void locked(PackingNfcOperation row,String token) {
        Bridge bridge=access(row.getItemId());packing.lock(bridge.getPackingId());
        PackingNfcOperation bind=operations.operation(row.getItemId(),"BIND");
        if(bind==null||!token.equals(bind.getLeaseToken()))throw new IllegalArgumentException("本次处理标识已失效，请刷新回执。");
    }
    private void state(PackingNfcOperation row,String token,String status,String error) {
        tx.execute(s->{locked(row,token);row.setChainStatus(status);row.setErrorCode(error);operations.state(row);updateBusiness(row);
            audit.record("PACKING_NFC_RESULT","TRACE_ITEM",row.getItemId(),row.getOperation()+" / "+status);return null;});
    }
    private void updateBusiness(PackingNfcOperation row) {
        PackingNfcOperation disable=operations.operation(row.getItemId(),"DISABLE");
        if(disable!=null) {
            String status="DISABLED:"+disable.getChainStatus();tags.updateStatus(row.getTagId(),status,"DISABLED");items.updateNfcStatus(row.getItemId(),status);
        } else {
            String status=("CONFIRMED".equals(row.getChainStatus())?"BOUND:":"BINDING:")+row.getChainStatus();
            tags.updateStatus(row.getTagId(),status,"ACTIVE");items.updateNfcStatus(row.getItemId(),status);
        }
    }
    private boolean emptyHash(String value){return value==null||value.matches("(?:0x)?0{64}");}
    @SuppressWarnings("unchecked") private Map<String,Object> map(String json){return (Map<String,Object>)payloads.readJson(json);}
}
