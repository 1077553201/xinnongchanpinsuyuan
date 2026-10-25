package org.nongchanpin.service;

import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.nongchanpin.chain.*;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.dto.SubmitCirculationEvidenceRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.Bridge;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.AccessContext;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Immutable, hash-linked public snapshots. Business operations never depend on a network transaction. */
@Service
public class CirculationEvidenceService {
    private final CirculationEvidenceMapper evidence;
    private final PackingTraceMapper packing;
    private final TraceItemMapper items;
    private final TraceEventMapper events;
    private final PackingEvidenceMapper production;
    private final ProductBatchMapper batches;
    private final ItemCirculationService circulation;
    private final PayloadService payloads;
    private final ChainService chain;
    private final ChainTransactionService transactions;
    private final AuditService audit;
    private final ContractConfig config;
    private final TransactionTemplate tx;

    public CirculationEvidenceService(CirculationEvidenceMapper evidence,PackingTraceMapper packing,TraceItemMapper items,TraceEventMapper events,PackingEvidenceMapper production,ProductBatchMapper batches,ItemCirculationService circulation,PayloadService payloads,ChainService chain,ChainTransactionService transactions,AuditService audit,ContractConfig config,PlatformTransactionManager manager) {
        this.evidence=evidence;this.packing=packing;this.items=items;this.events=events;this.production=production;this.batches=batches;this.circulation=circulation;this.payloads=payloads;this.chain=chain;this.transactions=transactions;this.audit=audit;this.config=config;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(Long itemId) {
        access(itemId);CirculationEvidenceEntity latest=evidence.latest(itemId);Map<String,Object> candidate=candidate(itemId,latest);
        List<Map<String,Object>> versions=new ArrayList<>();
        for(CirculationEvidenceEntity row:evidence.versions(itemId)){validate(row,events.findById(row.getEventId()));versions.add(summary(row));}
        boolean limit=latest!=null&&latest.getVersion()==50&&!payloads.canonicalJson(map(latest.getManifestJson()).get("circulation")).equals(payloads.canonicalJson(current(itemId)));
        return Map.of("manifest",candidate,"manifestHash",payloads.requestHash(candidate),"frozen",latest!=null&&latest.getManifestHash().equals(payloads.requestHash(candidate)),"versionLimitReached",limit,"versions",versions);
    }

    public Map<String,Object> submit(Long itemId,SubmitCirculationEvidenceRequest request) {
        AccessContext.write();String token=UUID.randomUUID().toString();
        CirculationEvidenceEntity row=tx.execute(s->prepare(itemId,request,token));
        try{run(row,token);}finally{tx.execute(s->{evidence.release(row.getId(),token);return null;});}
        return preview(itemId);
    }

    private CirculationEvidenceEntity prepare(Long itemId,SubmitCirculationEvidenceRequest request,String token) {
        Bridge bridge=access(itemId);
        // Match sales/create/receipt lock order: order -> packaging -> shipment.
        if(evidence.lockOrderForItem(itemId)==null)throw new IllegalArgumentException("请先关联实际销售出库。");
        packing.lock(bridge.getPackingId());Long shipment=evidence.shipmentForItem(itemId);if(shipment!=null)evidence.lockShipment(shipment);
        TraceItemEntity item=items.findById(itemId);
        if(!"CONFIRMED".equals(item.getChainStatus()) || !"ACTIVE".equals(item.getStatus()))throw new IllegalArgumentException("请先完成本码生产来源存证，并确认包装码仍可用。");
        CirculationEvidenceEntity latest=evidence.latest(itemId);
        if(latest!=null&&latest.getLeaseUntil()!=null&&latest.getLeaseUntil().isAfter(LocalDateTime.now()))throw new IllegalArgumentException("本码已有存证正在处理，请稍后刷新回执。");
        Map<String,Object> manifest=latest!=null&&latest.getManifestHash().equals(request.getExpectedManifestHash())?map(latest.getManifestJson()):candidate(itemId,latest);String json=payloads.canonicalJson(manifest),hash=payloads.payloadHash(json);
        if(!hash.equals(request.getExpectedManifestHash()))throw new IllegalArgumentException("逐件流转资料已变化，请刷新预览后重新核对。");
        if(latest==null || !hash.equals(latest.getManifestHash())) {
            if(latest!=null&&!"CONFIRMED".equals(latest.getChainStatus()))throw new IllegalArgumentException("请先核对并完成上一版存证，再提交后续版本。");
            TraceEventEntity event=new TraceEventEntity();event.setItemId(itemId);event.setBatchId(item.getBatchId());event.setEventType("LOGISTICS");event.setEventTime(LocalDateTime.now().withNano(0));event.setLocation("");event.setOperatorName("业务单据");event.setChainStatus("NOT_SUBMITTED");event.setStatus("ACTIVE");
            Map<String,Object> body=eventBody(item,event,Map.of("schema","ITEM_CIRCULATION_V1","manifestHash",hash,"manifest",manifest));
            event.setPayload(payloads.canonicalJson(body));event.setCanonicalPayloadHash(payloads.payloadHash(event.getPayload()));event.setEventHash(HashUtils.hashCanonicalTextPlainHex(event.getPayload()+itemId));events.insert(event);
            CirculationEvidenceEntity next=new CirculationEvidenceEntity();next.setItemId(itemId);next.setVersion(((Number)manifest.get("version")).intValue());next.setManifestJson(json);next.setManifestHash(hash);next.setPreviousHash(latest==null?null:latest.getManifestHash());next.setEventId(event.getId());next.setCreatedBy(AccessContext.require().getId());next.setChainStatus("NOT_SUBMITTED");evidence.insert(next);latest=next;
            audit.record("CIRCULATION_FREEZE","TRACE_ITEM",itemId,"流转版本 "+next.getVersion()+" / "+hash);
        }
        validate(latest,events.findById(latest.getEventId()));evidence.claim(latest.getId(),token,LocalDateTime.now().plusMinutes(5));
        audit.record("CIRCULATION_SUBMIT","TRACE_ITEM",itemId,"确认版本 "+latest.getVersion()+" / "+hash);return latest;
    }

    private Map<String,Object> candidate(Long itemId,CirculationEvidenceEntity latest) {
        Bridge bridge=packing.publicBridge(itemId);PackingTraceService.publicSource(bridge);
        PackingEvidenceModels.Snapshot source=production.snapshot(bridge.getPackingId());
        if(source==null)throw new IllegalArgumentException("请先冻结生产来源证据，再预览逐件流转存证。");
        PackingEvidenceService.validate(source,bridge);
        Map<String,Object> facts=current(itemId);
        if(latest!=null){validate(latest,events.findById(latest.getEventId()));Map<String,Object> old=map(latest.getManifestJson());if(payloads.canonicalJson(old.get("circulation")).equals(payloads.canonicalJson(facts)))return old;}
        int version=latest==null?1:latest.getVersion()+1;if(version>50)return map(latest.getManifestJson());
        Map<String,Object> manifest=payloads.map();manifest.put("schema","ITEM_CIRCULATION_V1");manifest.put("version",version);manifest.put("previousHash",latest==null?null:latest.getManifestHash());manifest.put("traceCodeHash",items.findById(itemId).getTraceCodeHash());manifest.put("sourceHash",bridge.getSourceHash());manifest.put("productionManifestHash",source.getManifestHash());manifest.put("circulation",facts);return manifest;
    }

    private boolean run(CirculationEvidenceEntity row,String token) {
        tx.execute(s->{locked(row,token);return null;});TraceItemEntity item=items.findById(row.getItemId());TraceEventEntity event=events.findById(row.getEventId());validate(row,event);
        try {
            Bridge bridge=packing.publicBridge(row.getItemId());PackingEvidenceModels.Snapshot source=production.snapshot(bridge.getPackingId());PackingEvidenceService.validate(source,bridge);
            ProductBatchEntity batch=batches.findById(item.getBatchId());Map<String,Object> body=map(source.getBatchPayload());
            ChainBatchView chainBatch=chain.getBatch(batch.getBatchCode());ChainItemView chainItem=chain.getItem(item.getTraceCodePlain());
            if(chainBatch==null||chainItem==null)throw new IllegalStateException();
            Map<String,Object> itemBody=Map.of("traceCodeHash",item.getTraceCodeHash(),"batchCode",batch.getBatchCode(),"traceUrl",item.getTraceUrl());
            if(!chainBatch.isExists()||!chainItem.isExists()||!HashUtils.hashBusinessIdHex(batch.getBatchCode()).equalsIgnoreCase(chainItem.getBatchIdHash())||!HashUtils.hashBusinessIdHex(body.get("productCode").toString()).equalsIgnoreCase(chainBatch.getProductIdHash())||!HashUtils.hashCanonicalTextHex(source.getBatchPayload()).equalsIgnoreCase(chainBatch.getPayloadHash())||!payloads.payloadHashWithPrefix(itemBody).equalsIgnoreCase(chainItem.getPayloadHash()))return state(row,token,"FAILED","SOURCE_MISMATCH");
            // bindItem starts in CREATED (1); CREATED, ACTIVE and SOLD may retain ordinary circulation evidence.
            boolean writable=chainItem.getStatus()!=null&&chainItem.getStatus().intValue()>=1&&chainItem.getStatus().intValue()<=3&&BigInteger.ONE.equals(chainBatch.getStatus());
            BigInteger count=chainItem.getTraceEventCount();if(count==null||count.signum()<0||count.compareTo(BigInteger.valueOf(200))>0)throw new IllegalStateException();
            CirculationEvidenceEntity previous=row.getVersion()>1?evidence.byVersion(row.getItemId(),row.getVersion()-1):null;
            TraceEventEntity previousEvent=previous==null?null:events.findById(previous.getEventId());
            if(previous!=null)validate(previous,previousEvent);
            boolean found=false,parentFound=previous==null;
            for(int index=0;index<count.intValue();index++){
                ChainTraceEventView actual=chain.getTraceEvent(item.getTraceCodePlain(),index);if(actual==null||actual.getEventIdHash()==null)throw new IllegalStateException();
                if(HashUtils.hashBusinessIdHex("TRACE_EVENT:"+event.getId()).equalsIgnoreCase(actual.getEventIdHash())){
                    if(found||!eventMatches(item.getTraceCodePlain(),event,actual,index))return state(row,token,"FAILED","HASH_MISMATCH");found=true;
                }
                if(previousEvent!=null&&HashUtils.hashBusinessIdHex("TRACE_EVENT:"+previousEvent.getId()).equalsIgnoreCase(actual.getEventIdHash()))parentFound=eventMatches(item.getTraceCodePlain(),previousEvent,actual,index);
            }
            if(found)return state(row,token,"CONFIRMED",null);
            if(!writable)return state(row,token,"FAILED","SOURCE_NOT_ACTIVE");
            if(!parentFound)return state(row,token,"FAILED","PREVIOUS_NOT_VERIFIED");
            if(count.intValue()>=200)return state(row,token,"FAILED","EVENT_LIMIT");
        }catch(RuntimeException e){return state(row,token,row.getTxId()==null?"NOT_SUBMITTED":"UNCERTAIN","CHAIN_READ_UNAVAILABLE");}
        if(row.getTxId()!=null){ChainTransactionEntity old=transactions.get(row.getTxId());boolean retry="CHAIN_DISABLED".equals(old.getChainStatus())||("FAILED".equals(old.getChainStatus())&&old.getReceiptStatus()!=null&&!old.getReceiptStatus().isBlank());if(!retry)return state(row,token,"UNCERTAIN","RECONCILE_REQUIRED");}
        if("CONFIRMED".equals(row.getChainStatus()))return state(row,token,"UNCERTAIN","CHAIN_RECORD_MISSING");
        ChainTransactionEntity pending=tx.execute(s->{locked(row,token);ChainTransactionEntity value=transactions.createPending("TRACE_EVENT_APPEND",event.getId(),HashUtils.hashBusinessIdHex("TRACE_EVENT:"+event.getId()),config.getAgriTraceAddress(),"TraceEventAppended","0x"+event.getCanonicalPayloadHash(),row.getManifestHash());row.setTxId(value.getId());row.setChainStatus("PENDING");row.setErrorCode(null);evidence.state(row);events.updateChainStatus(event.getId(),"PENDING");return value;});
        AppendTraceEventCommand command=new AppendTraceEventCommand();command.setItemCode(item.getTraceCodePlain());command.setEventId("TRACE_EVENT:"+event.getId());command.setEventType(8);command.setPayload(map(event.getPayload()));command.setOccurredAt(event.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());
        ChainTxResult receipt;
        try{receipt=chain.appendTraceEvent(command);}catch(RuntimeException e){return state(row,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");}
        if(receipt==null||receipt.getChainStatus()==null)return state(row,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");
        tx.execute(s->{locked(row,token);transactions.completeFromResult(pending,receipt);return null;});
        if(receipt.getChainStatus()==ChainStatus.CONFIRMED)return run(row,token);
        if(receipt.getChainStatus()==ChainStatus.CHAIN_DISABLED)return state(row,token,"CHAIN_DISABLED","CHAIN_DISABLED");
        boolean known=receipt.getReceiptStatus()!=null&&!receipt.getReceiptStatus().isBlank();return state(row,token,known?"FAILED":"UNCERTAIN",known?"TRANSACTION_FAILED":"CHAIN_RESULT_UNKNOWN");
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicView(Long itemId,List<TraceEventEntity> local,PublicTraceView.ChainVerification verification) {
        List<CirculationEvidenceEntity> rows=evidence.versions(itemId);if(rows.isEmpty())return null;
        Map<String,Object> facts=current(itemId);List<Map<String,Object>> versions=new ArrayList<>();Map<Long,Integer> indexes=new HashMap<>();for(int i=0;i<local.size();i++)indexes.put(local.get(i).getId(),i);
        boolean base="LIVE_CHAIN".equals(verification.getVerificationSource())&&verification.isBatchPayloadHashMatched()&&verification.isItemPayloadHashMatched();
        List<CirculationEvidenceEntity> ascending=new ArrayList<>(rows);Collections.reverse(ascending);Map<String,Boolean> proofs=new HashMap<>();
        for(CirculationEvidenceEntity row:ascending){Integer index=indexes.get(row.getEventId());TraceEventEntity event=index==null?null:local.get(index);validate(row,event);Map<String,Object> value=summary(row);boolean parent=row.getVersion()==1||Boolean.TRUE.equals(proofs.get(row.getPreviousHash()));boolean matched=base&&parent&&index!=null&&verification.getRecords().size()>index+2&&verification.getRecords().get(index+2).isPayloadHashMatched()&&"LIVE_CHAIN".equals(verification.getRecords().get(index+2).getVerificationSource());proofs.put(row.getManifestHash(),matched);value.put("verificationStatus",matched?"VERIFIED":"NOT_VERIFIED");versions.add(value);}Collections.reverse(versions);
        boolean current=payloads.canonicalJson(map(rows.get(0).getManifestJson()).get("circulation")).equals(payloads.canonicalJson(facts));
        return Map.of("currentMatchesLatest",current,"verificationStatus",current?versions.get(0).get("verificationStatus"):"NOT_VERIFIED","versions",versions);
    }

    private Map<String,Object> current(Long id){Map<String,Object> value=circulation.publicView(id);if(value==null)throw new IllegalArgumentException("请先将具体包装码关联到实际销售出库。");value=new LinkedHashMap<>(value);value.remove("chainStatus");return value;}
    private Map<String,Object> summary(CirculationEvidenceEntity row){Map<String,Object> value=new LinkedHashMap<>();value.put("version",row.getVersion());value.put("manifest",payloads.readJson(row.getManifestJson()));value.put("manifestHash",row.getManifestHash());value.put("createdAt",row.getCreatedAt());value.put("chainStatus",row.getChainStatus());value.put("txId",row.getTxId());value.put("errorCode",row.getErrorCode());return value;}
    private Bridge access(Long id){Bridge bridge=packing.publicBridge(id);if(bridge==null)throw new IllegalArgumentException("包装码不存在。");AccessContext.checkOrganization(packing.source(bridge.getPackingId()).getOrganizationId());return bridge;}
    private void locked(CirculationEvidenceEntity row,String token){Bridge bridge=access(row.getItemId());packing.lock(bridge.getPackingId());CirculationEvidenceEntity saved=evidence.find(row.getId());if(saved==null||!token.equals(saved.getLeaseToken()))throw new IllegalArgumentException("本次处理标识已失效，请刷新回执。");}
    private boolean state(CirculationEvidenceEntity row,String token,String status,String error){tx.execute(s->{locked(row,token);row.setChainStatus(status);row.setErrorCode(error);evidence.state(row);events.updateChainStatus(row.getEventId(),"UNCERTAIN".equals(status)?"PENDING":status);audit.record("CIRCULATION_RESULT","TRACE_ITEM",row.getItemId(),"版本 "+row.getVersion()+" / "+status);return null;});return "CONFIRMED".equals(status);}
    @SuppressWarnings("unchecked") private Map<String,Object> map(String value){Object parsed=payloads.readJson(value);if(!(parsed instanceof Map))throw new IllegalArgumentException("流转证据格式不正确。");return (Map<String,Object>)parsed;}
    private Map<String,Object> eventBody(TraceItemEntity item,TraceEventEntity event,Object content){Map<String,Object> body=payloads.map();body.put("traceCodeHash",item.getTraceCodeHash());body.put("eventType",event.getEventType());body.put("eventTime",event.getEventTime());body.put("location",event.getLocation());body.put("operatorName",event.getOperatorName());body.put("payload",content);return body;}
    private void validate(CirculationEvidenceEntity row,TraceEventEntity event){
        if(event==null||!row.getItemId().equals(event.getItemId())||!HashUtils.hashCanonicalTextPlainHex(row.getManifestJson()).equals(row.getManifestHash()))throw new IllegalArgumentException("流转证据摘要或归属不一致。");
        Map<String,Object> manifest=map(row.getManifestJson()),body=map(event.getPayload()),content=(Map<String,Object>)body.get("payload");
        String canonicalEvent=payloads.canonicalJson(body);
        TraceItemEntity item=items.findById(row.getItemId());Bridge bridge=packing.publicBridge(row.getItemId());PackingEvidenceModels.Snapshot source=production.snapshot(bridge.getPackingId());
        if(row.getVersion()>1){CirculationEvidenceEntity previous=evidence.byVersion(row.getItemId(),row.getVersion()-1);if(previous==null||!previous.getManifestHash().equals(row.getPreviousHash()))throw new IllegalArgumentException("流转版本的前序摘要不一致。");}
        if(!Objects.equals(manifest.get("version"),row.getVersion())||!Objects.equals(manifest.get("previousHash"),row.getPreviousHash())||!item.getTraceCodeHash().equals(manifest.get("traceCodeHash"))||!bridge.getSourceHash().equals(manifest.get("sourceHash"))||source==null||!source.getManifestHash().equals(manifest.get("productionManifestHash"))||!"ITEM_CIRCULATION_V1".equals(manifest.get("schema"))||content==null||!row.getManifestHash().equals(content.get("manifestHash"))||!payloads.canonicalJson(manifest).equals(payloads.canonicalJson(content.get("manifest")))||!HashUtils.hashCanonicalTextPlainHex(canonicalEvent).equals(event.getCanonicalPayloadHash())||!payloads.canonicalJson(eventBody(item,event,content)).equals(canonicalEvent))throw new IllegalArgumentException("流转版本、来源与存证事件不一致。");
    }
    public static boolean eventMatches(String code,TraceEventEntity local,ChainTraceEventView actual,long index){return HashUtils.hashBusinessIdHex(code).equalsIgnoreCase(actual.getItemCodeHash())&&BigInteger.valueOf(index).equals(actual.getIndex())&&BigInteger.valueOf(org.nongchanpin.constants.AgriTraceCodes.eventTypeCode(local.getEventType())).equals(actual.getEventType())&&BigInteger.valueOf(local.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond()).equals(actual.getOccurredAt())&&("0x"+local.getCanonicalPayloadHash()).equalsIgnoreCase(actual.getPayloadHash());}
}
