package org.nongchanpin.service;

import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.constants.AgriTraceCodes;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.command.ReceiptCall;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingEvidenceModels.Job;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TransactionReceiptService {
    private final EvidenceReconciliationService evidence;
    private final ChainTransactionMapper transactions;
    private final PackingEvidenceMapper production;
    private final PackingTraceMapper packing;
    private final CirculationEvidenceMapper circulation;
    private final QualityEvidenceMapper quality;
    private final SourceQualityMapper reports;
    private final PackingNfcMapper nfc;
    private final NfcTagMapper tags;
    private final TraceItemMapper items;
    private final ProductBatchMapper batches;
    private final TraceEventMapper events;
    private final PayloadService payloads;
    private final ChainService chain;
    private final ContractConfig config;
    private final AuditService audit;
    private final PlatformTransactionManager manager;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(String kind,Long id){return capture(kind,id).result;}

    public Map<String,Object> record(String kind,Long id,String expectedHash) {
        AccessContext.write();Capture observed=capture(kind,id);
        if(!Boolean.TRUE.equals(observed.result.get("canRecord"))||!observed.result.get("checkHash").equals(expectedHash))
            throw new IllegalArgumentException("原交易或回执核对结果已变化，请重新核对；不会重发交易。");
        TransactionTemplate tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.execute(s->{
            lockEvidence(observed.row);
            EvidenceReconciliationRow current=evidence.required(kind,id);evidence.validate(current);
            ReconciliationGuard.check(current.getFrozenHash(),current.getTxId(),observed.row.getFrozenHash(),observed.row.getTxId());
            if(current.getLeaseUntil()!=null&&current.getLeaseUntil().isAfter(LocalDateTime.now()))throw new IllegalArgumentException("原业务仍在处理中，请稍后核对。");
            ChainTransactionEntity saved=transactions.lockReceipt(current.getTxId());
            if(saved==null||!payloads.requestHash(saved).equals(observed.intentFingerprint))throw new IllegalArgumentException("原交易意图已变化，请重新核对回执。");
            ChainReceiptView receipt=observed.receipt;
            saved.setChainStatus(receipt.getReceiptStatus()==0?"CONFIRMED":"FAILED");
            saved.setBlockNumber(receipt.getBlockNumber().longValueExact());saved.setReceiptStatus(receipt.getReceiptStatus().toString());
            saved.setChainOperator(receipt.getOperator());if(saved.getConfirmedAt()==null)saved.setConfirmedAt(LocalDateTime.now());
            saved.setErrorMessage(receipt.getReceiptStatus()==0?null:"原始回执执行失败，状态 "+receipt.getReceiptStatus());
            saved.setReceiptVerifiedAt(LocalDateTime.now());saved.setReceiptVerificationHash(observed.receiptDigest);
            if(transactions.recordReceipt(saved)!=1)throw new IllegalArgumentException("原交易已变化，请重新核对。");
            audit.record("TRANSACTION_RECEIPT_RECORD",kind,id,"原交易 #"+saved.getId()+" / "+saved.getTxHash()+" / "+saved.getChainStatus()+" / 核对摘要 "+expectedHash);
            return null;
        });
        return preview(kind,id);
    }

    private Capture capture(String kind,Long id) {
        Capture c=new Capture();c.row=evidence.required(kind,id);
        String outcome="LOCAL_CONFLICT";
        try {
            evidence.validate(c.row);
            if(c.row.getTxId()==null)outcome="NO_INTENT";
            else {
                c.intent=transactions.findById(c.row.getTxId());if(c.intent==null)throw new IllegalArgumentException();
                c.intentFingerprint=payloads.requestHash(c.intent);
                if(c.intent.getTxHash()==null||c.intent.getTxHash().isBlank())outcome="MISSING_HASH";
                else if(!c.intent.getTxHash().matches("0x[0-9a-fA-F]{64}"))outcome="INVALID_HASH";
                else {
                    ReceiptCall call=expected(c.row,c.intent);
                    try {c.receipt=chain.inspectReceipt(call);outcome=ReceiptVerification.outcome(call,c.intent,c.receipt);}
                    catch(RuntimeException error){outcome="UNAVAILABLE";}
                }
            }
        } catch(AccessException error){throw error;}
        catch(RuntimeException error){outcome="LOCAL_CONFLICT";}
        Map<String,Object> receiptSummary=summary(c.receipt);
        c.receiptDigest=payloads.requestHash(receiptSummary);
        boolean matched=Set.of("SUCCESS_MATCHED","FAILED_MATCHED").contains(outcome);
        boolean already=matched&&c.intent!=null&&c.intent.getReceiptVerifiedAt()!=null&&c.receiptDigest.equals(c.intent.getReceiptVerificationHash());
        if(matched&&c.intent.getReceiptVerifiedAt()!=null&&!already){outcome="RECORDED_CONFLICT";matched=false;}
        boolean processing=c.row.getLeaseUntil()!=null&&c.row.getLeaseUntil().isAfter(LocalDateTime.now());
        c.result=new LinkedHashMap<>();c.result.put("record",c.row);c.result.put("outcome",outcome);
        c.result.put("canRecord",matched&&!already&&!processing);c.result.put("alreadyRecorded",already);c.result.put("processing",processing);
        c.result.put("transactionId",c.row.getTxId());c.result.put("txHash",c.intent==null?null:c.intent.getTxHash());
        c.result.put("storedChainStatus",c.intent==null?null:c.intent.getChainStatus());c.result.put("storedReceiptStatus",c.intent==null?null:c.intent.getReceiptStatus());
        c.result.put("verifiedAt",c.intent==null?null:c.intent.getReceiptVerifiedAt());c.result.put("receipt",receiptSummary);
        Map<String,Object> confirmation=new LinkedHashMap<>();confirmation.put("kind",kind);confirmation.put("id",id);confirmation.put("hash",c.row.getFrozenHash());
        confirmation.put("intent",c.intentFingerprint);confirmation.put("receipt",c.receiptDigest);confirmation.put("outcome",outcome);confirmation.put("canRecord",c.result.get("canRecord"));
        c.result.put("checkHash",payloads.requestHash(confirmation));c.result.put("checkedAt",LocalDateTime.now());return c;
    }

    private ReceiptCall expected(EvidenceReconciliationRow row,ChainTransactionEntity intent) {
        if(intent.getContractAddress()==null||!intent.getContractAddress().matches("0x[0-9a-fA-F]{40}")
                ||!ReceiptVerification.same(config.getAgriTraceAddress(),intent.getContractAddress()))throw new IllegalArgumentException("目标合约不一致。");
        ReceiptCall call=new ReceiptCall();call.setTxHash(intent.getTxHash());call.setContractAddress(intent.getContractAddress());
        org.nongchanpin.model.entity.PackingTraceModels.Bridge bridge=packing.bridge(row.getPackingId());
        ProductBatchEntity batch=batches.findById(bridge.getBatchId());
        TraceItemEntity item=row.getItemId()==null?null:items.findById(row.getItemId());
        String type,businessHash,payloadHash,requestHash;Long businessId;
        if("PRODUCTION".equals(row.getKind())) {
            Job job=production.findJob(row.getRecordId());type=job.getBusinessType();businessId=job.getBusinessId();payloadHash=job.getPayloadHash();requestHash=HashUtils.hashCanonicalTextPlainHex(job.getPayloadJson());
            if("BATCH_REGISTER".equals(type)) {
                Map<String,Object> body=map(job.getPayloadJson());String batchHash=HashUtils.hashBusinessIdHex(batch.getBatchCode()),productHash=HashUtils.hashBusinessIdHex(body.get("productCode").toString());
                long started=batch.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
                businessHash=batchHash;call.setMethod("registerBatch");call.setEventName("BatchRegistered");
                call.bytes(batchHash).bytes(productHash).bytes(payloadHash).uint("uint256",started);
                call.getEventFields().putAll(Map.of("batchIdHash",batchHash,"productIdHash",productHash,"payloadHash",payloadHash,"startedAt",Long.toString(started)));
            } else {
                String itemHash=HashUtils.hashBusinessIdHex(item.getTraceCodePlain()),batchHash=HashUtils.hashBusinessIdHex(batch.getBatchCode());
                businessHash=itemHash;call.setMethod("bindItem");call.setEventName("ItemBound");call.bytes(itemHash).bytes(batchHash).bytes(payloadHash);
                call.getEventFields().putAll(Map.of("itemCodeHash",itemHash,"batchIdHash",batchHash,"payloadHash",payloadHash));
            }
        } else if("CIRCULATION".equals(row.getKind())||"QUALITY".equals(row.getKind())) {
            Long eventId="QUALITY".equals(row.getKind())?quality.find(row.getRecordId()).getEventId():circulation.find(row.getRecordId()).getEventId();
            TraceEventEntity event=events.findById(eventId);String itemHash=HashUtils.hashBusinessIdHex(item.getTraceCodePlain()),eventHash=HashUtils.hashBusinessIdHex("TRACE_EVENT:"+eventId);
            int eventType=AgriTraceCodes.eventTypeCode(event.getEventType());long occurred=event.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
            type="TRACE_EVENT_APPEND";businessId=eventId;businessHash=eventHash;payloadHash="0x"+event.getCanonicalPayloadHash();requestHash=row.getFrozenHash();
            call.setMethod("appendTraceEvent");call.setEventName("TraceEventAppended");call.bytes(itemHash).bytes(eventHash).uint("uint8",eventType).bytes(payloadHash).uint("uint256",occurred);
            call.getEventFields().putAll(Map.of("itemCodeHash",itemHash,"eventIdHash",eventHash,"eventType",Integer.toString(eventType),"payloadHash",payloadHash,"occurredAt",Long.toString(occurred)));
        } else {
            PackingNfcOperation op=nfc.find(row.getRecordId());String uidHash="0x"+tags.findById(op.getTagId()).getUidHash(),itemHash=HashUtils.hashBusinessIdHex(item.getTraceCodePlain());
            type="PACKING_NFC_"+op.getOperation();businessId=op.getId();businessHash=uidHash;payloadHash="0x"+op.getPayloadHash();requestHash=op.getPayloadHash();
            if("BIND".equals(op.getOperation())) {
                call.setMethod("bindNfcTag");call.setEventName("NfcTagBound");call.bytes(itemHash).bytes(uidHash).bytes(payloadHash);
                call.getEventFields().putAll(Map.of("itemCodeHash",itemHash,"nfcUidHash",uidHash,"payloadHash",payloadHash));
            } else {
                call.setMethod("updateNfcStatus");call.setEventName("NfcStatusUpdated");call.bytes(uidHash).uint("uint8",3).bytes(payloadHash);
                call.getEventFields().putAll(Map.of("nfcUidHash",uidHash,"status","3","reasonHash",payloadHash));
            }
        }
        if(!type.equals(intent.getBusinessType())||!Objects.equals(businessId,intent.getBusinessId())
                ||!ReceiptVerification.same(businessHash,intent.getBusinessIdHash())||!ReceiptVerification.same(payloadHash,intent.getPayloadHash())
                ||!requestHash.equals(intent.getRequestHash())||!call.getEventName().equals(intent.getEventName()))throw new IllegalArgumentException("原意图与冻结操作不一致。");
        return call;
    }

    private void lockEvidence(EvidenceReconciliationRow row) {
        if("QUALITY".equals(row.getKind())) {
            QualityEvidenceModels.Evidence saved=quality.find(row.getRecordId());SourceQualityModels.Report report=reports.report(saved.getReportId());
            reports.lockHarvest(report.getHarvestId());reports.lock(report.getId());
        }
        packing.lock(row.getPackingId());
        switch(row.getKind()) {
            case "PRODUCTION":production.lockJob(row.getRecordId());break;
            case "CIRCULATION":circulation.lockFrozen(row.getRecordId());break;
            case "QUALITY":quality.lockFrozen(row.getRecordId());break;
            case "NFC":nfc.lockFrozen(row.getRecordId());break;
            default:throw new IllegalArgumentException("证据类型无效。");
        }
    }
    private Map<String,Object> summary(ChainReceiptView receipt) {
        Map<String,Object> result=new LinkedHashMap<>();if(receipt==null)return result;
        result.put("found",receipt.isFound());result.put("txHash",lower(receipt.getTransactionHash()));result.put("contractAddress",lower(receipt.getContractAddress()));
        result.put("operator",lower(receipt.getOperator()));result.put("blockNumber",receipt.getBlockNumber());result.put("receiptStatus",receipt.getReceiptStatus());
        result.put("inputMatched",receipt.isInputMatched());result.put("inputHash",receipt.getInputHash());
        List<Map<String,String>> normalized=new ArrayList<>();if(receipt.getEvents()!=null)for(Map<String,String> event:receipt.getEvents()){Map<String,String> fields=new LinkedHashMap<>();event.forEach((k,v)->fields.put(k,lower(v)));normalized.add(fields);}result.put("events",normalized);return result;
    }
    private String lower(String value){return value==null?null:value.toLowerCase(Locale.ROOT);}
    @SuppressWarnings("unchecked") private Map<String,Object> map(String json){return (Map<String,Object>)payloads.readJson(json);}
    private static class Capture { EvidenceReconciliationRow row;ChainTransactionEntity intent;ChainReceiptView receipt;String intentFingerprint,receiptDigest;Map<String,Object> result; }
}
