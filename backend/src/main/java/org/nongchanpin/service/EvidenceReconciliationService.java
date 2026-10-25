package org.nongchanpin.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.Bridge;
import org.nongchanpin.model.entity.PackingEvidenceModels.*;
import org.nongchanpin.model.vo.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Lists existing frozen work only. Neither inspection nor reconciliation can issue a chain write. */
@Service
@RequiredArgsConstructor
public class EvidenceReconciliationService {
    private static final Set<String> KINDS=Set.of("PRODUCTION","CIRCULATION","QUALITY","NFC");
    private static final Set<String> STATUSES=Set.of("ALL","ATTENTION","NOT_SUBMITTED","PENDING","UNCERTAIN","FAILED","CHAIN_DISABLED","CONFIRMED");
    private final EvidenceReconciliationMapper records;
    private final PackingEvidenceMapper production;
    private final CirculationEvidenceMapper circulation;
    private final QualityEvidenceMapper quality;
    private final PackingNfcMapper nfc;
    private final NfcTagMapper tags;
    private final PackingTraceMapper packing;
    private final TraceItemMapper items;
    private final ProductBatchMapper batches;
    private final TraceEventMapper events;
    private final ChainTransactionService transactions;
    private final PackingEvidenceService productionService;
    private final CirculationEvidenceService circulationService;
    private final QualityEvidenceService qualityService;
    private final PackingNfcService nfcService;
    private final PayloadService payloads;
    private final ChainService chain;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(String kind,String status,Long packingId,String code,int minAgeHours,int page) {
        if(!kind.isEmpty()&&!KINDS.contains(kind)||!STATUSES.contains(status)||page<1||page>1000000||minAgeHours<0||minAgeHours>87600||packingId!=null&&packingId<1||code.length()>128)
            throw new IllegalArgumentException("对账筛选条件无效。");
        Long org=AccessContext.organization();LocalDateTime before=LocalDateTime.now().minusHours(minAgeHours);
        long count=records.count(org,kind,status,packingId,code,before),current=Math.min(page,Math.max(1,(count+19)/20));
        return Map.of("rows",records.rows(org,kind,status,packingId,code,before,(current-1)*20),"total",count,"page",current,"limit",20,"checkedAt",LocalDateTime.now());
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> inspect(String kind,Long id) {
        EvidenceReconciliationRow row=required(kind,id);String outcome;
        ChainTransactionEntity intent=null;
        try {
            if(row.getTxId()!=null)intent=transactions.get(row.getTxId());
            validate(row);
            try { outcome=readChain(row); }
            catch(IllegalArgumentException error){outcome="CONFLICT";}
            catch(RuntimeException error){outcome="UNAVAILABLE";}
        } catch(AccessException error){throw error;}
        catch(RuntimeException error){outcome="LOCAL_CONFLICT";}
        boolean processing=row.getLeaseUntil()!=null&&row.getLeaseUntil().isAfter(LocalDateTime.now());
        boolean recordable="MATCHED".equals(outcome)&&!processing&&!"CONFIRMED".equals(row.getChainStatus());
        boolean definitive=intent!=null&&("CHAIN_DISABLED".equals(intent.getChainStatus())||"FAILED".equals(intent.getChainStatus())&&intent.getReceiptStatus()!=null&&!intent.getReceiptStatus().isBlank());
        String disposition="MATCHED".equals(outcome)?"EXISTING_RECORD":!"NOT_FOUND".equals(outcome)?"STOP":row.getTxId()==null&&!"CONFIRMED".equals(row.getChainStatus())||definitive?"BUSINESS_REVIEW":"UNKNOWN_TRANSACTION";
        Map<String,Object> confirmation=new LinkedHashMap<>();confirmation.put("kind",kind);confirmation.put("id",id);
        confirmation.put("frozenHash",row.getFrozenHash());confirmation.put("txId",row.getTxId());confirmation.put("localStatus",row.getChainStatus());
        confirmation.put("transactionStatus",intent==null?null:intent.getChainStatus());confirmation.put("receiptStatus",intent==null?null:intent.getReceiptStatus());
        confirmation.put("outcome",outcome);confirmation.put("recordable",recordable);
        Map<String,Object> result=new LinkedHashMap<>();result.put("record",row);result.put("outcome",outcome);
        result.put("disposition",disposition);result.put("processing",processing);result.put("canRecord",recordable);
        result.put("checkHash",payloads.requestHash(confirmation));result.put("checkedAt",LocalDateTime.now());
        result.put("transactionStatus",intent==null?null:intent.getChainStatus());result.put("receiptStatus",intent==null?null:intent.getReceiptStatus());
        return result;
    }

    public Map<String,Object> reconcile(String kind,Long id,String expectedCheckHash) {
        AccessContext.write();Map<String,Object> fresh=inspect(kind,id);
        if(!Boolean.TRUE.equals(fresh.get("canRecord"))||!fresh.get("checkHash").equals(expectedCheckHash))
            throw new IllegalArgumentException("查链结果或原交易已变化，请重新核对；本入口不会发送链交易。");
        EvidenceReconciliationRow row=(EvidenceReconciliationRow)fresh.get("record");
        switch(kind) {
            case "PRODUCTION":productionService.reconcileFrozen(id,row.getFrozenHash(),row.getTxId());break;
            case "CIRCULATION":circulationService.reconcileFrozen(id,row.getFrozenHash(),row.getTxId());break;
            case "QUALITY":qualityService.reconcileFrozen(id,row.getFrozenHash(),row.getTxId());break;
            case "NFC":nfcService.reconcileFrozen(id,row.getFrozenHash(),row.getTxId());break;
            default:throw new IllegalArgumentException("对账类型无效。");
        }
        return inspect(kind,id);
    }

    EvidenceReconciliationRow required(String kind,Long id) {
        if(!KINDS.contains(kind)||id==null||id<1)throw new IllegalArgumentException("对账记录无效。");
        EvidenceReconciliationRow row=records.find(kind,id,AccessContext.organization());
        if(row==null)throw new AccessException(404,"对账记录不存在或无权查看。");
        AccessContext.checkOrganization(row.getOrganizationId());return row;
    }

    void validate(EvidenceReconciliationRow row) {
        PackingEvidenceService.validate(production.snapshot(row.getPackingId()),packing.bridge(row.getPackingId()));
        switch(row.getKind()) {
            case "PRODUCTION": {
                Job job=production.findJob(row.getRecordId());productionService.validateFrozen(job);
                ReconciliationGuard.check(job.getPayloadHash(),job.getTxId(),row.getFrozenHash(),row.getTxId());break;
            }
            case "CIRCULATION": {
                CirculationEvidenceEntity saved=circulation.find(row.getRecordId());circulationService.validate(saved,events.findById(saved.getEventId()));
                ReconciliationGuard.check(saved.getManifestHash(),saved.getTxId(),row.getFrozenHash(),row.getTxId());break;
            }
            case "QUALITY": {
                QualityEvidenceModels.Evidence saved=quality.find(row.getRecordId());qualityService.validate(saved,events.findById(saved.getEventId()));
                ReconciliationGuard.check(saved.getManifestHash(),saved.getTxId(),row.getFrozenHash(),row.getTxId());break;
            }
            case "NFC": {
                PackingNfcOperation saved=nfc.find(row.getRecordId());nfcService.validate(saved);
                ReconciliationGuard.check(saved.getPayloadHash(),saved.getTxId(),row.getFrozenHash(),row.getTxId());break;
            }
            default:throw new IllegalArgumentException("对账类型无效。");
        }
    }

    private String readChain(EvidenceReconciliationRow row) {
        Bridge bridge=packing.bridge(row.getPackingId());Snapshot snapshot=production.snapshot(row.getPackingId());
        PackingEvidenceService.validate(snapshot,bridge);
        ProductBatchEntity batch=batches.findById(bridge.getBatchId());Map<String,Object> batchBody=map(snapshot.getBatchPayload());
        ChainBatchView chainBatch=chain.getBatch(batch.getBatchCode());if(chainBatch==null)throw new IllegalStateException();
        if("PRODUCTION".equals(row.getKind())&&"BATCH_REGISTER".equals(row.getAction())) {
            if(!chainBatch.isExists())return "NOT_FOUND";
            return batchMatches(chainBatch,snapshot,batchBody)?"MATCHED":"CONFLICT";
        }
        if(!chainBatch.isExists())return "SOURCE_MISSING";
        if(!batchMatches(chainBatch,snapshot,batchBody))return "CONFLICT";
        TraceItemEntity item=items.findById(row.getItemId());ChainItemView chainItem=chain.getItem(item.getTraceCodePlain());
        if(chainItem==null)throw new IllegalStateException();
        if(!chainItem.isExists())return "PRODUCTION".equals(row.getKind())?"NOT_FOUND":"SOURCE_MISSING";
        String itemHash=payloads.payloadHashWithPrefix(Map.of("traceCodeHash",item.getTraceCodeHash(),"batchCode",batch.getBatchCode(),"traceUrl",item.getTraceUrl()));
        if(!HashUtils.hashBusinessIdHex(batch.getBatchCode()).equalsIgnoreCase(chainItem.getBatchIdHash())||!itemHash.equalsIgnoreCase(chainItem.getPayloadHash()))return "CONFLICT";
        if("PRODUCTION".equals(row.getKind()))return row.getFrozenHash().equalsIgnoreCase(chainItem.getPayloadHash())?"MATCHED":"CONFLICT";
        if("NFC".equals(row.getKind()))return readNfc(row,item,chainItem);
        return readEvents(row,item,chainItem);
    }

    private boolean batchMatches(ChainBatchView actual,Snapshot snapshot,Map<String,Object> body) {
        return HashUtils.hashBusinessIdHex(body.get("productCode").toString()).equalsIgnoreCase(actual.getProductIdHash())
            &&HashUtils.hashCanonicalTextHex(snapshot.getBatchPayload()).equalsIgnoreCase(actual.getPayloadHash());
    }

    private String readNfc(EvidenceReconciliationRow row,TraceItemEntity item,ChainItemView chainItem) {
        PackingNfcOperation saved=nfc.find(row.getRecordId()),bind=nfc.operation(row.getItemId(),"BIND");
        nfcService.validate(bind);NfcTagEntity tag=tags.findById(saved.getTagId());ChainNfcView actual=chain.getNfcTag("0x"+tag.getUidHash());
        if(actual==null)throw new IllegalStateException();if(!actual.isExists())return "NOT_FOUND";
        if(!("0x"+tag.getUidHash()).equalsIgnoreCase(actual.getNfcUidHash())||!("0x"+tag.getUidHash()).equalsIgnoreCase(chainItem.getNfcUidHash())
                ||!HashUtils.hashBusinessIdHex(item.getTraceCodePlain()).equalsIgnoreCase(actual.getItemCodeHash())||!("0x"+bind.getPayloadHash()).equalsIgnoreCase(actual.getPayloadHash()))return "CONFLICT";
        if(actual.getStatus()==null||actual.getStatus().intValue()<1||actual.getStatus().intValue()>3)return "CONFLICT";
        if("BIND".equals(row.getAction()))return "MATCHED";
        if(BigInteger.ONE.equals(actual.getStatus()))return "NOT_FOUND";
        return BigInteger.valueOf(3).equals(actual.getStatus())&&("0x"+saved.getPayloadHash()).equalsIgnoreCase(actual.getReasonHash())?"MATCHED":"CONFLICT";
    }

    private String readEvents(EvidenceReconciliationRow row,TraceItemEntity item,ChainItemView chainItem) {
        Map<String,TraceEventEntity> expected=new LinkedHashMap<>();String target=null;int next=1;
        if("CIRCULATION".equals(row.getKind())) {
            List<CirculationEvidenceEntity> versions=new ArrayList<>(circulation.versions(row.getItemId()));Collections.reverse(versions);
            for(CirculationEvidenceEntity version:versions)if(version.getVersion()<=row.getVersion()) {
                if(version.getVersion()!=next++)return "LOCAL_CONFLICT";
                TraceEventEntity event=events.findById(version.getEventId());circulationService.validate(version,event);
                String hash=HashUtils.hashBusinessIdHex("TRACE_EVENT:"+event.getId());expected.put(hash,event);if(version.getId().equals(row.getRecordId()))target=hash;
            }
        } else {
            List<QualityEvidenceModels.Evidence> versions=new ArrayList<>(quality.versions(row.getReportId(),row.getItemId()));Collections.reverse(versions);
            for(QualityEvidenceModels.Evidence version:versions)if(version.getVersion()<=row.getVersion()) {
                if(version.getVersion()!=next++)return "LOCAL_CONFLICT";
                TraceEventEntity event=events.findById(version.getEventId());qualityService.validate(version,event);
                String hash=HashUtils.hashBusinessIdHex("TRACE_EVENT:"+event.getId());expected.put(hash,event);if(version.getId().equals(row.getRecordId()))target=hash;
            }
        }
        if(target==null||next!=row.getVersion()+1)return "LOCAL_CONFLICT";
        BigInteger count=chainItem.getTraceEventCount();if(count==null||count.signum()<0)throw new IllegalStateException();
        if(count.compareTo(BigInteger.valueOf(200))>0)return "READ_LIMIT";
        Set<String> found=new HashSet<>();
        for(int index=0;index<count.intValue();index++) {
            ChainTraceEventView actual=chain.getTraceEvent(item.getTraceCodePlain(),index);
            if(actual==null||actual.getEventIdHash()==null)throw new IllegalStateException();
            String key=actual.getEventIdHash().toLowerCase(Locale.ROOT);TraceEventEntity local=expected.get(key);
            if(local!=null&&(!found.add(key)||!CirculationEvidenceService.eventMatches(item.getTraceCodePlain(),local,actual,index)))return "CONFLICT";
        }
        if(!found.contains(target))return "NOT_FOUND";
        return found.size()==expected.size()?"MATCHED":"PREVIOUS_MISSING";
    }

    @SuppressWarnings("unchecked") private Map<String,Object> map(String json){return (Map<String,Object>)payloads.readJson(json);}
}
