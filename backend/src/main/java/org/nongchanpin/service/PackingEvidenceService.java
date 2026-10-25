package org.nongchanpin.service;

import java.time.*;
import java.util.*;
import org.nongchanpin.chain.*;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.*;
import org.nongchanpin.model.dto.SubmitPackingEvidenceRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingEvidenceModels.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.AccessContext;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PackingEvidenceService {
    private final PackingEvidenceMapper evidence;
    private final PackingTraceMapper traces;
    private final ProductBatchMapper batches;
    private final ProductMapper products;
    private final TraceItemMapper items;
    private final PackingManifestService manifests;
    private final PayloadService payloads;
    private final ChainService chain;
    private final ChainTransactionService transactions;
    private final ContractConfig config;
    private final AuditService audit;
    private final TransactionTemplate tx;
    public PackingEvidenceService(PackingEvidenceMapper evidence,PackingTraceMapper traces,ProductBatchMapper batches,ProductMapper products,TraceItemMapper items,PackingManifestService manifests,PayloadService payloads,ChainService chain,ChainTransactionService transactions,ContractConfig config,AuditService audit,PlatformTransactionManager manager) {
        this.evidence=evidence;this.traces=traces;this.batches=batches;this.products=products;this.items=items;this.manifests=manifests;this.payloads=payloads;this.chain=chain;this.transactions=transactions;this.config=config;this.audit=audit;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(Long id) {
        Source source=access(id);Bridge bridge=bridge(id);Snapshot saved=evidence.snapshot(id);
        String json=saved==null?payloads.canonicalJson(manifests.build(source,bridge)):saved.getManifestJson();
        if(saved!=null)validate(saved,bridge);
        Map<String,Object> result=new LinkedHashMap<>();result.put("manifest",payloads.readJson(json));result.put("manifestHash",HashUtils.hashCanonicalTextPlainHex(json));result.put("frozen",saved!=null);result.put("jobs",evidence.jobs(id));
        if(saved!=null)result.put("createdAt",saved.getCreatedAt());return result;
    }
    public Map<String,Object> submit(Long id,SubmitPackingEvidenceRequest request) {
        AccessContext.write();
        if(request.getItemIds()==null||request.getItemIds().isEmpty()||request.getItemIds().size()>20||new HashSet<>(request.getItemIds()).size()!=request.getItemIds().size())throw new IllegalArgumentException("请明确选择 1–20 个不重复的包装码。");
        String token=UUID.randomUUID().toString();
        List<Job> jobs=tx.execute(s->prepare(id,request,token));
        try {
            for(Job job:jobs){if(!run(job,token))break;}
        } finally {tx.execute(s->{evidence.release(id,token);return null;});}
        return preview(id);
    }
    private List<Job> prepare(Long id,SubmitPackingEvidenceRequest request,String token) {
        Source source=access(id);traces.lock(id);Bridge bridge=bridge(id);PackingTraceService.publicSource(bridge);
        for(Long itemId:request.getItemIds())if(!id.equals(evidence.packingForItem(itemId)))throw new IllegalArgumentException("所选码不属于本包装或没有访问权限。");
        Snapshot saved=evidence.snapshot(id);
        if(saved!=null&&saved.getLeaseUntil()!=null&&saved.getLeaseUntil().isAfter(LocalDateTime.now()))throw new IllegalArgumentException("本包装已有存证正在处理，请稍后刷新回执。");
        ProductBatchEntity batch=batches.findById(bridge.getBatchId());
        if(saved==null) {
            String json=payloads.canonicalJson(manifests.build(source,bridge)),hash=HashUtils.hashCanonicalTextPlainHex(json);
            if(!hash.equals(request.getExpectedManifestHash()))throw new IllegalArgumentException("生产资料已变化，请刷新预览后重新核对。");
            ProductEntity product=products.findById(batch.getProductId());if(product==null)throw new IllegalArgumentException("产品来源不存在。");
            Map<String,Object> metadata=Map.of("sourceType","PACKING_V2","packingId",id,"sourceHash",bridge.getSourceHash(),"evidenceHash",hash,"evidenceVersion",1);
            if(evidence.batchMetadata(batch.getId(),payloads.canonicalJson(metadata))!=1)throw new IllegalArgumentException("原批次状态不允许冻结新证据，请先核对。");
            Map<String,Object> body=payloads.map();body.put("batchCode",batch.getBatchCode());body.put("productCode",product.getProductCode());body.put("productionArea",batch.getProductionArea());body.put("plantDate",batch.getPlantDate());body.put("harvestDate",batch.getHarvestDate());body.put("metadata",metadata);
            saved=new Snapshot();saved.setPackingId(id);saved.setManifestJson(json);saved.setManifestHash(hash);saved.setBatchPayload(payloads.canonicalJson(body));saved.setCreatedBy(AccessContext.require().getId());evidence.insertSnapshot(saved);
            audit.record("PACKING_EVIDENCE_FREEZE","PACKING",id,"生产证据版本 1 / "+hash);
        }
        validate(saved,bridge);
        if(!saved.getManifestHash().equals(request.getExpectedManifestHash()))throw new IllegalArgumentException("确认的证据版本不一致，请刷新核对。");
        evidence.claim(id,token,LocalDateTime.now().plusMinutes(5));
        List<Job> jobs=new ArrayList<>();jobs.add(ensure(id,"BATCH_REGISTER",batch.getId(),saved.getBatchPayload()));
        for(Long itemId:request.getItemIds()) {
            TraceItemEntity item=items.findById(itemId);if(item==null||!"ACTIVE".equals(item.getStatus()))throw new IllegalArgumentException("所选码状态不允许存证。");
            Map<String,Object> body=payloads.map();body.put("traceCodeHash",item.getTraceCodeHash());body.put("batchCode",batch.getBatchCode());body.put("traceUrl",item.getTraceUrl());
            jobs.add(ensure(id,"ITEM_BIND",itemId,payloads.canonicalJson(body)));
        }
        audit.record("PACKING_EVIDENCE_SUBMIT","PACKING",id,"证据 "+saved.getManifestHash()+" / 确认码 "+request.getItemIds());
        return jobs;
    }
    private Job ensure(Long packing,String type,Long business,String json) {
        Job job=evidence.job(type,business);String hash=HashUtils.hashCanonicalTextHex(json);
        if(job!=null){if(!hash.equalsIgnoreCase(job.getPayloadHash())||!HashUtils.hashCanonicalTextHex(job.getPayloadJson()).equalsIgnoreCase(job.getPayloadHash()))throw new IllegalArgumentException("原存证内容与当前资料不一致，禁止覆盖。");return job;}
        job=new Job();job.setPackingId(packing);job.setBusinessType(type);job.setBusinessId(business);job.setPayloadJson(json);job.setPayloadHash(hash);job.setChainStatus("NOT_SUBMITTED");evidence.insertJob(job);return job;
    }
    private boolean run(Job job,String token) {
        tx.execute(s->{locked(job.getPackingId(),token);return null;});
        Map<String,Object> body=map(job.getPayloadJson());boolean batch="BATCH_REGISTER".equals(job.getBusinessType());
        ProductBatchEntity lot=batches.findById(bridge(job.getPackingId()).getBatchId());
        TraceItemEntity item=batch?null:items.findById(job.getBusinessId());
        try {
            String actual=null;
            if(batch){ChainBatchView view=chain.getBatch(lot.getBatchCode());if(view==null)throw new IllegalStateException();if(view.isExists()){if(!HashUtils.hashBusinessIdHex(body.get("productCode").toString()).equalsIgnoreCase(view.getProductIdHash()))return state(job,token,"FAILED","SOURCE_MISMATCH");actual=view.getPayloadHash();if(actual==null)throw new IllegalStateException();}}
            else {ChainItemView view=chain.getItem(item.getTraceCodePlain());if(view==null)throw new IllegalStateException();if(view.isExists()){if(!HashUtils.hashBusinessIdHex(lot.getBatchCode()).equalsIgnoreCase(view.getBatchIdHash()))return state(job,token,"FAILED","SOURCE_MISMATCH");actual=view.getPayloadHash();if(actual==null)throw new IllegalStateException();}}
            if(actual!=null){if(!job.getPayloadHash().equalsIgnoreCase(actual))return state(job,token,"FAILED","HASH_MISMATCH");return state(job,token,"CONFIRMED",null);}
        } catch(RuntimeException e){return state(job,token,job.getTxId()==null?"NOT_SUBMITTED":"UNCERTAIN","CHAIN_READ_UNAVAILABLE");}
        if(job.getTxId()!=null) {
            ChainTransactionEntity old=transactions.get(job.getTxId());
            boolean definitive="CHAIN_DISABLED".equals(old.getChainStatus())||("FAILED".equals(old.getChainStatus())&&old.getReceiptStatus()!=null&&!old.getReceiptStatus().isBlank());
            if(!definitive)return state(job,token,"UNCERTAIN","RECONCILE_REQUIRED");
        }
        if("CONFIRMED".equals(job.getChainStatus()))return state(job,token,"UNCERTAIN","CHAIN_RECORD_MISSING");
        ChainTransactionEntity pending=tx.execute(s->{locked(job.getPackingId(),token);ChainTransactionEntity p=transactions.createPending(job.getBusinessType(),job.getBusinessId(),HashUtils.hashBusinessIdHex(batch?lot.getBatchCode():item.getTraceCodePlain()),config.getAgriTraceAddress(),batch?"BatchRegistered":"ItemBound",job.getPayloadHash(),HashUtils.hashCanonicalTextPlainHex(job.getPayloadJson()));job.setTxId(p.getId());job.setChainStatus("PENDING");job.setErrorCode(null);evidence.updateJob(job);businessStatus(job,"PENDING");return p;});
        ChainTxResult result;
        try {
            if(batch){RegisterBatchCommand command=new RegisterBatchCommand();command.setBatchCode(lot.getBatchCode());command.setProductCode(body.get("productCode").toString());command.setPayload(body);command.setStartedAt(lot.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());result=chain.registerBatch(command);}
            else {BindItemCommand command=new BindItemCommand();command.setItemCode(item.getTraceCodePlain());command.setBatchCode(lot.getBatchCode());command.setPayload(body);result=chain.bindItem(command);}
        } catch(RuntimeException e){return state(job,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");}
        if(result==null||result.getChainStatus()==null)return state(job,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");
        final ChainTxResult receipt=result;
        tx.execute(s->{locked(job.getPackingId(),token);transactions.completeFromResult(pending,receipt);return null;});
        if(result.getChainStatus()==ChainStatus.CONFIRMED)return run(job,token);
        if(result.getChainStatus()==ChainStatus.CHAIN_DISABLED)return state(job,token,"CHAIN_DISABLED","CHAIN_DISABLED");
        boolean known=result.getReceiptStatus()!=null&&!result.getReceiptStatus().isBlank();
        return state(job,token,known?"FAILED":"UNCERTAIN",known?"TRANSACTION_FAILED":"CHAIN_RESULT_UNKNOWN");
    }
    private boolean state(Job job,String token,String status,String error) {
        tx.execute(s->{locked(job.getPackingId(),token);job.setChainStatus(status);job.setErrorCode(error);evidence.updateJob(job);businessStatus(job,"UNCERTAIN".equals(status)?"PENDING":status);audit.record("PACKING_EVIDENCE_RESULT","PACKING",job.getPackingId(),job.getBusinessType()+" #"+job.getBusinessId()+" / "+status);return null;});return "CONFIRMED".equals(status);
    }
    private void businessStatus(Job job,String status) {if("BATCH_REGISTER".equals(job.getBusinessType()))batches.updateChainStatus(job.getBusinessId(),status);else items.updateChainStatus(job.getBusinessId(),status);}
    private void locked(Long id,String token) {access(id);traces.lock(id);Snapshot saved=evidence.snapshot(id);if(saved==null||!token.equals(saved.getLeaseToken()))throw new IllegalArgumentException("本次处理标识已失效，请刷新存证回执。");}
    private Source access(Long id) {Source source=traces.source(id);if(source==null)throw new IllegalArgumentException("包装不存在。");AccessContext.checkOrganization(source.getOrganizationId());return source;}
    private Bridge bridge(Long id) {Bridge bridge=traces.bridge(id);if(bridge==null)throw new IllegalArgumentException("请先生成包装码，再预览存证资料。");return bridge;}
    @SuppressWarnings("unchecked") private Map<String,Object> map(String json) {return (Map<String,Object>)payloads.readJson(json);}
    public static void validate(Snapshot saved,Bridge bridge) {
        if(!HashUtils.hashCanonicalTextPlainHex(saved.getManifestJson()).equals(saved.getManifestHash()))throw new IllegalArgumentException("证据摘要不一致，不能提交或声称核验通过。");
        try {
            com.fasterxml.jackson.databind.JsonNode manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(saved.getManifestJson()),body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(saved.getBatchPayload());
            if(!manifest.path("source").path("sourceHash").asText().equals(bridge.getSourceHash())||!body.path("metadata").path("sourceHash").asText().equals(bridge.getSourceHash())||!body.path("metadata").path("evidenceHash").asText().equals(saved.getManifestHash()))throw new IllegalArgumentException();
        } catch(Exception e){throw new IllegalArgumentException("来源、证据版本与存证内容不一致。");}
    }
}
