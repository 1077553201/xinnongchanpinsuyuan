package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.RejectionRequests.*;
import org.nongchanpin.model.entity.RejectionModels.*;
import org.nongchanpin.model.entity.WarehouseEntity;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Custody of a rejected complete package is separate from available inventory and financial settlement. */
@Service
@RequiredArgsConstructor
public class RejectionHandlingService {
    private final RejectionMapper records;
    private final PackingTraceMapper packing;
    private final SalesMapper sales;
    private final InventoryMapper inventory;
    private final PayloadService payloads;
    private final AuditService audit;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(Long dispatchId,Long itemId) {
        Source source=source(dispatchId,itemId);String json=payloads.canonicalJson(sourceBody(source));
        Map<String,Object> result=new LinkedHashMap<>();result.put("source",source);result.put("sourceHash",payloads.payloadHash(json));
        Case existing=records.byItem(itemId);if(existing!=null)result.put("existingCaseId",existing.getId());return result;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(Long warehouseId,String status,String code,int page) {
        if(!Set.of("ALL","OPEN","RETURNING","QUARANTINED","DISPOSED").contains(status)||page<1||page>1000000||code.length()>128)
            throw new IllegalArgumentException("请核对处置筛选与分页条件。");
        if(warehouseId!=null)warehouse(warehouseId,null);
        Long org=AccessContext.organization();long total=records.count(org,warehouseId,status,code),current=Math.min(page,Math.max(1,(total+19)/20));
        return Map.of("rows",records.list(org,warehouseId,status,code,(current-1)*20),"total",total,"page",current,"limit",20,
                "quarantineUnits",records.quarantineUnits(org,warehouseId));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id) {
        Case row=required(id);List<Event> events=records.events(id);validate(row,events);
        return Map.of("record",row,"source",source(row.getDispatchId(),row.getItemId()),"events",events);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> create(Create request) {
        AccessContext.write();key(request.getRequestKey());String reason=text(request.getReason(),500,"登记原因");
        Source observed=source(request.getDispatchId(),request.getItemId());sales.lockOrder(observed.getOrderId());packing.lock(observed.getPackingId());
        Source source=source(request.getDispatchId(),request.getItemId());String json=payloads.canonicalJson(sourceBody(source)),sourceHash=payloads.payloadHash(json);
        if(!sourceHash.equals(request.getExpectedSourceHash()))throw new IllegalArgumentException("原拒收来源已变化，请重新预览核对。");
        String requestHash=payloads.requestHash(Map.of("actor",AccessContext.require().getId(),"sourceHash",sourceHash,"reason",reason));
        Case old=records.byKey(request.getRequestKey());
        if(old!=null){AccessContext.checkOrganization(old.getOrganizationId());if(!old.getRequestHash().equals(requestHash))throw new IllegalArgumentException("确认标识已用于其他登记内容。");return detail(old.getId());}
        if(records.byItem(source.getItemId())!=null)throw new IllegalArgumentException("本包装码已有拒收处置单，请查看原记录，不能重复建单。");
        Case row=new Case();row.setOrganizationId(source.getOrganizationId());row.setDispatchId(source.getDispatchId());row.setPackingId(source.getPackingId());row.setItemId(source.getItemId());
        row.setSourceJson(json);row.setSourceHash(sourceHash);row.setStatus("OPEN");row.setRevision(0);row.setPackageUnit(source.getPackageUnit());row.setNetWeightKg(source.getNetWeightKg());
        row.setLastPhysicalAt(source.getReceiptOccurredAt());row.setReason(reason);row.setRecordedBy(AccessContext.require().getId());row.setRequestKey(request.getRequestKey());row.setRequestHash(requestHash);
        row.setInitialHash(initialHash(row));row.setStateHash(row.getInitialHash());records.insert(row);
        audit.record("REJECTION_OPEN","REJECTION_CASE",row.getId(),"拒收包装码 #"+row.getItemId()+" / 原来源 "+sourceHash);
        return detail(row.getId());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> append(Long id,Append request) {
        AccessContext.write();key(request.getRequestKey());Case observed=required(id);Source source=source(observed.getDispatchId(),observed.getItemId());
        sales.lockOrder(source.getOrderId());packing.lock(observed.getPackingId());Case row=records.lock(id);
        List<Event> previous=records.events(id);validate(row,previous);
        String requestHash=payloads.requestHash(Map.of("actor",AccessContext.require().getId(),"caseId",id,"request",request));
        Event replay=records.eventByKey(request.getRequestKey());
        if(replay!=null){if(!id.equals(replay.getCaseId())||!requestHash.equals(replay.getRequestHash()))throw new IllegalArgumentException("确认标识已用于其他处置内容。");return detail(id);}
        if(!row.getStateHash().equals(request.getExpectedStateHash()))throw new IllegalArgumentException("处置版本已变化，请刷新核对后重新确认。");
        if(row.getRevision()>=100)throw new IllegalArgumentException("本单已达 100 条历史上限，请保留历史并联系管理员核对。");
        Event event=new Event();event.setCaseId(id);event.setRevision(row.getRevision()+1);event.setKind(request.getKind());
        event.setPerformerName(text(request.getPerformerName(),128,"实际办理人员"));event.setNote(text(request.getNote(),1000,"处置说明"));
        event.setReference(optional(request.getReference(),128));event.setPreviousHash(row.getStateHash());event.setRecordedBy(AccessContext.require().getId());event.setRecordedAt(now());
        event.setRequestKey(request.getRequestKey());event.setRequestHash(requestHash);
        if("ANNOTATION".equals(request.getKind())) {
            if(request.getTargetRevision()==null||request.getTargetRevision()<0||request.getTargetRevision()>row.getRevision())throw new IllegalArgumentException("请明确说明针对原登记或哪条已有记录。");
            if(request.getWarehouseId()!=null||!blank(request.getQuarantineLocation()))throw new IllegalArgumentException("更正说明不改变仓库或隔离位置。");
            event.setTargetRevision(request.getTargetRevision());event.setOccurredAt(event.getRecordedAt());
        } else {
            if(request.getTargetRevision()!=null)throw new IllegalArgumentException("实际处置操作不指定更正对象。");
            LocalDateTime time=request.getOccurredAt();
            if(time==null||time.isAfter(now())||time.isBefore(row.getLastPhysicalAt()))throw new IllegalArgumentException("实际发生时间不能晚于现在或早于原拒收/最近实际处置。");
            event.setOccurredAt(time.withNano(time.getNano()/1000*1000));
            if("QUARANTINE_RECEIVE".equals(request.getKind())) {
                if(request.getWarehouseId()==null)throw new IllegalArgumentException("请确认实物收到的隔离仓库。");
                warehouse(request.getWarehouseId(),row.getOrganizationId());event.setWarehouseId(request.getWarehouseId());event.setQuarantineLocation(text(request.getQuarantineLocation(),256,"隔离位置"));
            } else if(request.getWarehouseId()!=null||!blank(request.getQuarantineLocation()))throw new IllegalArgumentException("本操作不改变原隔离仓库。");
            nextStatus(row.getStatus(),request.getKind());
        }
        event.setPayloadJson(payloads.canonicalJson(eventBody(event)));event.setEventHash(payloads.payloadHash(event.getPayloadJson()));records.insertEvent(event);
        apply(row,event);records.state(row);
        audit.record("REJECTION_EVENT","REJECTION_CASE",id,event.getKind()+" / 版本 "+event.getRevision()+" / "+event.getEventHash());return detail(id);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicView(Long itemId) {
        Case row=records.byItem(itemId);if(row==null)return null;List<Event> history=records.events(row.getId());validate(row,history);
        List<Map<String,Object>> events=new ArrayList<>();
        for(Event e:history){Map<String,Object> value=new LinkedHashMap<>();value.put("kind",e.getKind());value.put("occurredAt",e.getOccurredAt());value.put("revision",e.getRevision());if(e.getTargetRevision()!=null)value.put("targetRevision",e.getTargetRevision());events.add(value);}
        return Map.of("status",row.getStatus(),"revision",row.getRevision(),"handlingHash",row.getStateHash(),"events",events);
    }

    private Source source(Long dispatch,Long item) {
        if(dispatch==null||dispatch<1||item==null||item<1)throw new IllegalArgumentException("请指定原销售出库和完整包装码。");
        Source source=records.source(dispatch,item);if(source==null)throw new IllegalArgumentException("本码尚无本笔出库的逐件收货结果。");
        AccessContext.checkOrganization(source.getOrganizationId());
        org.nongchanpin.model.entity.PackingTraceModels.Source original=packing.source(source.getPackingId());
        if(original==null||!Objects.equals(original.getOrganizationId(),source.getOrganizationId()))throw new IllegalArgumentException("拒收单与包装所属机构不一致。");
        checkSource(source);return source;
    }
    private void checkSource(Source source){if(!"REJECTED".equals(source.getReceiptOutcome())||source.getReceiptEventId()==null||source.getReceiptOccurredAt()==null||source.getReceiptRecordedAt()==null)throw new IllegalArgumentException("仅可为已逐件核对拒收的包装码办理；不能依据汇总推断。");}
    private Case required(Long id){Case row=records.find(id);if(row==null)throw new IllegalArgumentException("拒收处置记录不存在。");AccessContext.checkOrganization(row.getOrganizationId());return row;}
    private WarehouseEntity warehouse(Long id,Long org){WarehouseEntity row=inventory.warehouse(id);if(row==null)throw new IllegalArgumentException("仓库不存在。");AccessContext.checkOrganization(row.getOrganizationId());if(org!=null&&!org.equals(row.getOrganizationId()))throw new IllegalArgumentException("隔离仓库须属于原包装的同一机构。");return row;}

    private Map<String,Object> sourceBody(Source s){Map<String,Object> body=payloads.map();body.put("schema","REJECTED_PACKAGE_V1");body.put("organizationId",s.getOrganizationId());body.put("orderId",s.getOrderId());body.put("dispatchId",s.getDispatchId());body.put("packingId",s.getPackingId());body.put("itemId",s.getItemId());body.put("traceCodeHash",s.getTraceCodeHash());body.put("packingSourceHash",s.getSourceHash());body.put("receiptEventId",s.getReceiptEventId());body.put("receiptOutcome",s.getReceiptOutcome());body.put("receiptOccurredAt",s.getReceiptOccurredAt());body.put("receiptRecordedAt",s.getReceiptRecordedAt());body.put("packageCount",1);body.put("packageUnit",s.getPackageUnit());body.put("netWeightKg",s.getNetWeightKg());return body;}
    private String initialHash(Case row){return payloads.requestHash(Map.of("schema","REJECTION_CASE_V1","sourceHash",row.getSourceHash(),"reason",row.getReason(),"actor",row.getRecordedBy(),"requestKey",row.getRequestKey()));}
    private Map<String,Object> eventBody(Event e){Map<String,Object> body=payloads.map();body.put("schema","REJECTION_EVENT_V1");body.put("caseId",e.getCaseId());body.put("revision",e.getRevision());body.put("kind",e.getKind());body.put("occurredAt",e.getOccurredAt());body.put("warehouseId",e.getWarehouseId());body.put("quarantineLocation",e.getQuarantineLocation());body.put("performerName",e.getPerformerName());body.put("note",e.getNote());body.put("reference",e.getReference());body.put("targetRevision",e.getTargetRevision());body.put("previousHash",e.getPreviousHash());body.put("recordedBy",e.getRecordedBy());body.put("recordedAt",e.getRecordedAt());body.put("requestKey",e.getRequestKey());body.put("requestHash",e.getRequestHash());return body;}
    private void validate(Case row,List<Event> history) {
        if(row==null||history.size()>100||!payloads.payloadHash(row.getSourceJson()).equals(row.getSourceHash())||!initialHash(row).equals(row.getInitialHash()))throw new IllegalArgumentException("原拒收来源或初始记录摘要不一致。");
        Source source=records.source(row.getDispatchId(),row.getItemId());if(source==null)throw new IllegalArgumentException("原逐件拒收来源缺失。");checkSource(source);
        if(!row.getOrganizationId().equals(source.getOrganizationId())||!row.getPackingId().equals(source.getPackingId())||!row.getPackageUnit().equals(source.getPackageUnit())||row.getNetWeightKg().compareTo(source.getNetWeightKg())!=0||!payloads.canonicalJson(sourceBody(source)).equals(row.getSourceJson()))throw new IllegalArgumentException("原逐件拒收来源已变化，不能覆盖历史。");
        Case expected=new Case();expected.setStatus("OPEN");expected.setRevision(0);expected.setStateHash(row.getInitialHash());expected.setLastPhysicalAt(source.getReceiptOccurredAt());
        for(Event event:history) {
            if(!row.getId().equals(event.getCaseId())||event.getRevision()!=expected.getRevision()+1||!expected.getStateHash().equals(event.getPreviousHash())
                    ||!payloads.payloadHash(event.getPayloadJson()).equals(event.getEventHash())||!payloads.canonicalJson(eventBody(event)).equals(event.getPayloadJson()))throw new IllegalArgumentException("处置历史的版本、原记录或摘要不一致。");
            if("ANNOTATION".equals(event.getKind())){if(event.getTargetRevision()==null||event.getTargetRevision()<0||event.getTargetRevision()>expected.getRevision())throw new IllegalArgumentException("说明引用的历史版本不正确。");}
            else if(event.getOccurredAt().isBefore(expected.getLastPhysicalAt()))throw new IllegalArgumentException("实际处置时间顺序不一致。");
            apply(expected,event);
        }
        if(!Objects.equals(row.getStatus(),expected.getStatus())||!Objects.equals(row.getRevision(),expected.getRevision())||!Objects.equals(row.getStateHash(),expected.getStateHash())||!Objects.equals(row.getWarehouseId(),expected.getWarehouseId())||!Objects.equals(row.getQuarantineLocation(),expected.getQuarantineLocation())||!Objects.equals(row.getLastPhysicalAt(),expected.getLastPhysicalAt()))throw new IllegalArgumentException("处置当前状态与完整历史不一致。");
    }
    private void apply(Case row,Event event){if(!"ANNOTATION".equals(event.getKind())){row.setStatus(nextStatus(row.getStatus(),event.getKind()));row.setLastPhysicalAt(event.getOccurredAt());if("QUARANTINE_RECEIVE".equals(event.getKind())){if(event.getWarehouseId()==null||blank(event.getQuarantineLocation()))throw new IllegalArgumentException("隔离收货信息不完整。");row.setWarehouseId(event.getWarehouseId());row.setQuarantineLocation(event.getQuarantineLocation());}}row.setRevision(event.getRevision());row.setStateHash(event.getEventHash());}
    static String nextStatus(String status,String kind){if("RETURN_DISPATCH".equals(kind)&&"OPEN".equals(status))return "RETURNING";if("QUARANTINE_RECEIVE".equals(kind)&&Set.of("OPEN","RETURNING").contains(status))return "QUARANTINED";if("DISPOSE".equals(kind)&&"QUARANTINED".equals(status))return "DISPOSED";throw new IllegalArgumentException("当前状态不允许这项实际处置，不能跳过隔离收货或重复办理。");}
    private void key(String key){if(key==null||!key.matches("[A-Za-z0-9-]{36}"))throw new IllegalArgumentException("确认标识无效，请刷新办理内容。");}
    private String text(String value,int max,String name){if(blank(value)||value.trim().length()>max)throw new IllegalArgumentException("请填写不超过 "+max+" 字的"+name+"。");return value.trim();}
    private String optional(String value,int max){return blank(value)?null:text(value,max,"参考编号");}
    private boolean blank(String value){return value==null||value.trim().isEmpty();}
    private LocalDateTime now(){LocalDateTime value=LocalDateTime.now();return value.withNano(value.getNano()/1000*1000);}
}
