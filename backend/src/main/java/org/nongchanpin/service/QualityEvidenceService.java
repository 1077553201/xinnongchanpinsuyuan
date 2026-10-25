package org.nongchanpin.service;

import static org.nongchanpin.service.CirculationEvidenceService.eventMatches;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.nongchanpin.chain.*;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.dto.QualityEvidenceRequests.Submit;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.QualityEvidenceModels.Evidence;
import org.nongchanpin.model.entity.PackingTraceModels.Bridge;
import org.nongchanpin.model.entity.SourceQualityModels.Report;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.AccessContext;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

/** A report's declared sample scope never implies that every related code has an on-chain proof. */
@Service
public class QualityEvidenceService {
    private final QualityEvidenceMapper evidence;
    private final SourceQualityMapper reports;
    private final SourceQualityService quality;
    private final PackingTraceMapper packing;
    private final TraceItemMapper items;
    private final TraceEventMapper events;
    private final PackingEvidenceMapper production;
    private final ProductBatchMapper batches;
    private final PayloadService payloads;
    private final ChainService chain;
    private final ChainTransactionService transactions;
    private final AuditService audit;
    private final ContractConfig config;
    private final TransactionTemplate tx;
    public QualityEvidenceService(QualityEvidenceMapper evidence,SourceQualityMapper reports,SourceQualityService quality,PackingTraceMapper packing,TraceItemMapper items,TraceEventMapper events,PackingEvidenceMapper production,ProductBatchMapper batches,PayloadService payloads,ChainService chain,ChainTransactionService transactions,AuditService audit,ContractConfig config,PlatformTransactionManager manager){this.evidence=evidence;this.reports=reports;this.quality=quality;this.packing=packing;this.items=items;this.events=events;this.production=production;this.batches=batches;this.payloads=payloads;this.chain=chain;this.transactions=transactions;this.audit=audit;this.config=config;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(Long reportId,String code){
        Long itemId=resolve(reportId,code);Evidence latest=evidence.latest(reportId,itemId);Map<String,Object> candidate=candidate(reportId,itemId,latest);List<Map<String,Object>> versions=new ArrayList<>();for(Evidence row:evidence.versions(reportId,itemId)){validate(row,events.findById(row.getEventId()));versions.add(summary(row));}
        boolean limit=latest!=null&&latest.getVersion()==20&&!sameFacts(latest,quality.publicFacts(reportId,itemId));return Map.of("manifest",candidate,"manifestHash",payloads.requestHash(candidate),"traceCode",items.findById(itemId).getTraceCodePlain(),"frozen",latest!=null&&latest.getManifestHash().equals(payloads.requestHash(candidate)),"versionLimitReached",limit,"versions",versions);
    }
    public Map<String,Object> submit(Long reportId,Submit request){AccessContext.write();Long itemId=resolve(reportId,request.getTraceCode());String token=UUID.randomUUID().toString();Evidence row=tx.execute(s->prepare(reportId,itemId,request,token));try{run(row,token);}finally{tx.execute(s->{evidence.release(row.getId(),token);return null;});}return preview(reportId,request.getTraceCode());}
    private Evidence prepare(Long reportId,Long itemId,Submit request,String token){
        Report report=quality.checkedReport(reportId);reports.lockHarvest(report.getHarvestId());reports.lock(reportId);Bridge bridge=access(reportId,itemId);packing.lock(bridge.getPackingId());quality.privateContent(reportId);
        TraceItemEntity item=items.findById(itemId);if(!"CONFIRMED".equals(item.getChainStatus())||!"ACTIVE".equals(item.getStatus()))throw new IllegalArgumentException("请先完成本码生产来源存证，并确认包装码仍可用。");
        Evidence latest=evidence.latest(reportId,itemId);if(latest!=null&&latest.getLeaseUntil()!=null&&latest.getLeaseUntil().isAfter(LocalDateTime.now()))throw new IllegalArgumentException("本报告与包装码已有存证处理中，请稍后刷新回执。");
        Map<String,Object> manifest=latest!=null&&latest.getManifestHash().equals(request.getExpectedManifestHash())?map(latest.getManifestJson()):candidate(reportId,itemId,latest);String json=payloads.canonicalJson(manifest),hash=payloads.payloadHash(json);if(!hash.equals(request.getExpectedManifestHash()))throw new IllegalArgumentException("报告公开状态或替代关系已变化，请刷新预览后核对。");
        if(latest==null||!hash.equals(latest.getManifestHash())){
            if(latest!=null&&!"CONFIRMED".equals(latest.getChainStatus()))throw new IllegalArgumentException("请先核对并完成上一冻结版本，不能跳过未知结果。");
            TraceEventEntity event=new TraceEventEntity();event.setItemId(itemId);event.setBatchId(item.getBatchId());event.setEventType("QUALITY_TEST");event.setEventTime(LocalDateTime.now().withNano(0));event.setLocation("");event.setOperatorName("经营方留档");event.setChainStatus("NOT_SUBMITTED");event.setStatus("ACTIVE");event.setPayload(payloads.canonicalJson(eventBody(item,event,Map.of("schema","QUALITY_REPORT_EVIDENCE_V1","manifestHash",hash,"manifest",manifest))));event.setCanonicalPayloadHash(payloads.payloadHash(event.getPayload()));event.setEventHash(HashUtils.hashCanonicalTextPlainHex(event.getPayload()+itemId+":"+reportId));events.insert(event);
            Evidence next=new Evidence();next.setReportId(reportId);next.setItemId(itemId);next.setVersion(((Number)manifest.get("version")).intValue());next.setManifestJson(json);next.setManifestHash(hash);next.setPreviousHash(latest==null?null:latest.getManifestHash());next.setEventId(event.getId());next.setCreatedBy(AccessContext.require().getId());next.setChainStatus("NOT_SUBMITTED");evidence.insert(next);latest=next;audit.record("QUALITY_EVIDENCE_FREEZE","QUALITY_REPORT",reportId,"包装码 #"+itemId+" / 版本 "+next.getVersion()+" / "+hash);
        }
        validate(latest,events.findById(latest.getEventId()));evidence.claim(latest.getId(),token,LocalDateTime.now().plusMinutes(5));audit.record("QUALITY_EVIDENCE_SUBMIT","QUALITY_REPORT",reportId,"包装码 #"+itemId+" / 确认摘要 "+hash);return latest;
    }
    private Map<String,Object> candidate(Long reportId,Long itemId,Evidence latest){
        Bridge bridge=access(reportId,itemId);PackingTraceService.publicSource(bridge);PackingEvidenceModels.Snapshot source=production.snapshot(bridge.getPackingId());if(source==null)throw new IllegalArgumentException("请先冻结本码的生产来源证据。");PackingEvidenceService.validate(source,bridge);Map<String,Object> facts=quality.publicFacts(reportId,itemId);
        if(latest!=null){validate(latest,events.findById(latest.getEventId()));if(sameFacts(latest,facts)||latest.getVersion()>=20)return map(latest.getManifestJson());}
        Map<String,Object> value=payloads.map();value.put("schema","QUALITY_REPORT_EVIDENCE_V1");value.put("version",latest==null?1:latest.getVersion()+1);value.put("previousHash",latest==null?null:latest.getManifestHash());value.put("traceCodeHash",items.findById(itemId).getTraceCodeHash());value.put("sourceHash",bridge.getSourceHash());value.put("productionManifestHash",source.getManifestHash());value.put("reportFacts",facts);return value;
    }

    private boolean run(Evidence row,String token) {
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
            Evidence previous=row.getVersion()>1?evidence.byVersion(row.getReportId(),row.getItemId(),row.getVersion()-1):null;
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
        AppendTraceEventCommand command=new AppendTraceEventCommand();command.setItemCode(item.getTraceCodePlain());command.setEventId("TRACE_EVENT:"+event.getId());command.setEventType(5);command.setPayload(map(event.getPayload()));command.setOccurredAt(event.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());
        ChainTxResult receipt;
        try{receipt=chain.appendTraceEvent(command);}catch(RuntimeException e){return state(row,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");}
        if(receipt==null||receipt.getChainStatus()==null)return state(row,token,"UNCERTAIN","CHAIN_RESULT_UNKNOWN");
        tx.execute(s->{locked(row,token);transactions.completeFromResult(pending,receipt);return null;});
        if(receipt.getChainStatus()==ChainStatus.CONFIRMED)return run(row,token);
        if(receipt.getChainStatus()==ChainStatus.CHAIN_DISABLED)return state(row,token,"CHAIN_DISABLED","CHAIN_DISABLED");
        boolean known=receipt.getReceiptStatus()!=null&&!receipt.getReceiptStatus().isBlank();return state(row,token,known?"FAILED":"UNCERTAIN",known?"TRANSACTION_FAILED":"CHAIN_RESULT_UNKNOWN");
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicEvidence(Long reportId,Long itemId,List<TraceEventEntity> local,PublicTraceView.ChainVerification verification){
        List<Evidence> rows=evidence.versions(reportId,itemId);if(rows.isEmpty())return null;Map<Long,Integer> indexes=new HashMap<>();for(int i=0;i<local.size();i++)indexes.put(local.get(i).getId(),i);boolean base="LIVE_CHAIN".equals(verification.getVerificationSource())&&verification.isBatchPayloadHashMatched()&&verification.isItemPayloadHashMatched();
        List<Evidence> ascending=new ArrayList<>(rows);Collections.reverse(ascending);Map<String,Boolean> proofs=new HashMap<>();List<Map<String,Object>> versions=new ArrayList<>();for(Evidence row:ascending){Integer index=indexes.get(row.getEventId());validate(row,index==null?null:local.get(index));boolean parent=row.getVersion()==1||Boolean.TRUE.equals(proofs.get(row.getPreviousHash()));boolean matched=base&&parent&&index!=null&&verification.getRecords().size()>index+2&&verification.getRecords().get(index+2).isPayloadHashMatched()&&"LIVE_CHAIN".equals(verification.getRecords().get(index+2).getVerificationSource());proofs.put(row.getManifestHash(),matched);Map<String,Object> value=summary(row);value.put("verificationStatus",matched?"VERIFIED":"NOT_VERIFIED");versions.add(value);}Collections.reverse(versions);boolean current=sameFacts(rows.get(0),quality.publicFacts(reportId,itemId));return Map.of("currentMatchesLatest",current,"verificationStatus",current?versions.get(0).get("verificationStatus"):"NOT_VERIFIED","versions",versions);
    }
    private Long resolve(Long reportId,String code){Report report=quality.checkedReport(reportId);if(report.getPublishedAt()==null)throw new IllegalArgumentException("请先核对公开报告，再生成对应码的质检证据。");if(code==null||code.trim().isEmpty()||code.length()>128)throw new IllegalArgumentException("请输入完整包装码。");TraceItemEntity item=items.findByTraceCodeHash(HashUtils.hashBusinessIdPlainHex(code));if(item==null)throw new IllegalArgumentException("包装码不存在或不适用于本报告。");access(reportId,item.getId());return item.getId();}
    private Bridge access(Long reportId,Long itemId){quality.checkedReport(reportId);Bridge bridge=packing.publicBridge(itemId);if(bridge==null)throw new IllegalArgumentException("请选择已生成的来源包装码。");quality.publicFacts(reportId,itemId);return bridge;}
    private boolean sameFacts(Evidence row,Map<String,Object> facts){return payloads.canonicalJson(map(row.getManifestJson()).get("reportFacts")).equals(payloads.canonicalJson(facts));}
    private void locked(Evidence row,String token){Bridge bridge=access(row.getReportId(),row.getItemId());packing.lock(bridge.getPackingId());Evidence saved=evidence.find(row.getId());if(saved==null||!token.equals(saved.getLeaseToken()))throw new IllegalArgumentException("本次处理标识已失效，请刷新质检存证回执。");}
    private boolean state(Evidence row,String token,String status,String error){tx.execute(s->{locked(row,token);row.setChainStatus(status);row.setErrorCode(error);evidence.state(row);events.updateChainStatus(row.getEventId(),"UNCERTAIN".equals(status)?"PENDING":status);audit.record("QUALITY_EVIDENCE_RESULT","QUALITY_REPORT",row.getReportId(),"包装码 #"+row.getItemId()+" / 版本 "+row.getVersion()+" / "+status);return null;});return "CONFIRMED".equals(status);}
    private Map<String,Object> summary(Evidence row){Map<String,Object> value=new LinkedHashMap<>();value.put("version",row.getVersion());value.put("manifest",payloads.readJson(row.getManifestJson()));value.put("manifestHash",row.getManifestHash());value.put("createdAt",row.getCreatedAt());value.put("chainStatus",row.getChainStatus());value.put("txId",row.getTxId());value.put("errorCode",row.getErrorCode());return value;}
    @SuppressWarnings("unchecked") private Map<String,Object> map(String json){Object value=payloads.readJson(json);if(!(value instanceof Map))throw new IllegalArgumentException("质检证据格式不正确。");return (Map<String,Object>)value;}
    private Map<String,Object> eventBody(TraceItemEntity item,TraceEventEntity event,Object content){Map<String,Object> body=payloads.map();body.put("traceCodeHash",item.getTraceCodeHash());body.put("eventType",event.getEventType());body.put("eventTime",event.getEventTime());body.put("location",event.getLocation());body.put("operatorName",event.getOperatorName());body.put("payload",content);return body;}
    private void validate(Evidence row,TraceEventEntity event){
        if(event==null||!row.getItemId().equals(event.getItemId())||!HashUtils.hashCanonicalTextPlainHex(row.getManifestJson()).equals(row.getManifestHash()))throw new IllegalArgumentException("质检证据摘要或包装码归属不一致。");
        Map<String,Object> manifest=map(row.getManifestJson()),facts=(Map<String,Object>)manifest.get("reportFacts"),body=map(event.getPayload()),content=(Map<String,Object>)body.get("payload");TraceItemEntity item=items.findById(row.getItemId());Bridge bridge=packing.publicBridge(row.getItemId());PackingEvidenceModels.Snapshot source=production.snapshot(bridge.getPackingId());Report report=reports.report(row.getReportId());
        if(row.getVersion()>1){Evidence previous=evidence.byVersion(row.getReportId(),row.getItemId(),row.getVersion()-1);if(previous==null||!previous.getManifestHash().equals(row.getPreviousHash()))throw new IllegalArgumentException("质检证据前序版本摘要不一致。");}
        if(!Objects.equals(manifest.get("version"),row.getVersion())||!Objects.equals(manifest.get("previousHash"),row.getPreviousHash())||!"QUALITY_REPORT_EVIDENCE_V1".equals(manifest.get("schema"))||!item.getTraceCodeHash().equals(manifest.get("traceCodeHash"))||!bridge.getSourceHash().equals(manifest.get("sourceHash"))||source==null||!source.getManifestHash().equals(manifest.get("productionManifestHash"))||facts==null||!String.valueOf(row.getReportId()).equals(String.valueOf(facts.get("reportId")))||report==null||!report.getManifestHash().equals(facts.get("reportManifestHash"))||!report.getManifestJson().equals(payloads.canonicalJson(facts.get("report")))||content==null||!row.getManifestHash().equals(content.get("manifestHash"))||!payloads.canonicalJson(manifest).equals(payloads.canonicalJson(content.get("manifest")))||!event.getCanonicalPayloadHash().equals(payloads.requestHash(body))||!payloads.canonicalJson(eventBody(item,event,content)).equals(payloads.canonicalJson(body)))throw new IllegalArgumentException("报告来源、文件摘要与冻结质检事件不一致。");
    }
}
