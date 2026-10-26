package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.TransportModels.Shipment;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class TransportPlanService {
    private final TransportMapper transport;
    private final TransportPlanMapper plans;
    private final SalesMapper sales;
    private final InventoryMapper inventory;
    private final PayloadService payloads;
    private final AuditService audit;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id){Shipment current=required(id),original=plans.original(id);List<TransportPlanAction> history=plans.history(id);validate(original,current,history);return Map.of("original",original,"current",current,"history",history,"revision",history.size(),"cancelBlockers",blockers(current,"CANCEL"),"replaceBlockers",blockers(current,"REPLACE"));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(Long id,@Valid TransportPlanRequests.Draft request){return candidate(id,request);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> confirm(Long id,@Valid TransportPlanRequests.Confirm request){
        AccessContext.write();Shipment observed=required(id);sales.lockOrder(observed.getOrderId());inventory.lockPacking(observed.getPackingId());transport.lock(id);
        key(request.getRequestKey());String requestHash=payloads.requestHash(Map.of("actor",AccessContext.require().getId(),"shipmentId",id,"request",request));
        TransportPlanAction replay=plans.byKey(request.getRequestKey());if(replay!=null){if(!id.equals(replay.getShipmentId())||!requestHash.equals(replay.getRequestHash()))throw new IllegalArgumentException("确认标识已用于其他安排内容。");return detail(id);}
        Map<String,Object> candidate=candidate(id,request);if(Boolean.FALSE.equals(candidate.get("canConfirm")))throw new IllegalArgumentException("本安排状态或下游依赖不允许办理，请重新核对阻止原因。");
        if(!candidate.get("impactHash").equals(request.getExpectedImpactHash()))throw new IllegalArgumentException("运输安排、有效出库或关联已变化，请重新预览。");
        Shipment current=required(id);List<TransportPlanAction> previous=plans.history(id);TransportPlanAction row=new TransportPlanAction();row.setShipmentId(id);row.setRevision(previous.size()+1);row.setAction(request.getAction());row.setReason(text(request.getReason(),800,"办理原因"));
        if("REPLACE".equals(row.getAction())){TransportRequests.Create draft=request.getReplacement();SalesDispatchEntity dispatch=sales.dispatch(current.getDispatchId());row.setShipmentCode(draft.getShipmentCode());row.setCarrier(text(draft.getCarrier(),128,"承运方"));row.setTrackingReference(optional(draft.getTrackingReference(),128));row.setDriverName(text(draft.getDriverName(),128,"运输联系人"));row.setDriverContact(optional(draft.getDriverContact(),128));row.setNotes(optional(draft.getNotes(),1000));row.setPackageCount(dispatch.getPackageCount());row.setPackageUnit(dispatch.getPackageUnit());row.setCorrectionRecordedAt(dispatch.getLastCorrectionAt());try{plans.claimCode(row.getShipmentCode(),id);}catch(org.springframework.dao.DuplicateKeyException e){throw new IllegalArgumentException("运输编码已被其他当前或历史安排使用，请重新预览并更换编码。");}}
        row.setImpactJson(payloads.canonicalJson(candidate.get("impact")));row.setImpactHash((String)candidate.get("impactHash"));row.setPreviousHash(previous.isEmpty()?initialHash(plans.original(id)):previous.get(previous.size()-1).getActionHash());row.setRecordedBy(AccessContext.require().getId());row.setRecordedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));row.setRequestKey(request.getRequestKey());row.setRequestHash(requestHash);row.setActionHash(payloads.requestHash(body(row)));plans.insert(row);
        transport.status(id,"CANCEL".equals(row.getAction())?"CANCELLED":"PLANNED");audit.record("TRANSPORT_PLAN_"+row.getAction(),"TRANSPORT",id,"安排版本 "+row.getRevision()+" / "+row.getActionHash());return detail(id);
    }
    private Map<String,Object> candidate(Long id,TransportPlanRequests.Draft request){
        Shipment current=required(id),original=plans.original(id);List<TransportPlanAction> history=plans.history(id);validate(original,current,history);
        if(history.size()>=100)throw new IllegalArgumentException("本运输档案已达 100 条安排历史上限，请联系管理员核对。");
        if(!Set.of("CANCEL","REPLACE").contains(request.getAction()))throw new IllegalArgumentException("请选择撤销或替代安排。");text(request.getReason(),800,"办理原因");
        SalesDispatchEntity dispatch=sales.dispatch(current.getDispatchId());List<String> blocked=new ArrayList<>(blockers(current,request.getAction()));Map<String,Object> replacement=payloads.map();
        if("REPLACE".equals(request.getAction())){
            TransportRequests.Create draft=request.getReplacement();if(draft==null||!current.getDispatchId().equals(draft.getDispatchId()))throw new IllegalArgumentException("替代安排须沿用本笔销售出库，不能换来源。");
            if(draft.getShipmentCode()==null||!draft.getShipmentCode().matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("请填写有效的新运输编码。");
            if(plans.codeCount(draft.getShipmentCode())>0)blocked.add("运输编码已用于现有或历史安排，不能复用。");
            replacement.put("shipmentCode",draft.getShipmentCode());replacement.put("carrier",text(draft.getCarrier(),128,"承运方"));replacement.put("trackingReference",optional(draft.getTrackingReference(),128));replacement.put("driverName",text(draft.getDriverName(),128,"运输联系人"));replacement.put("driverContact",optional(draft.getDriverContact(),128));replacement.put("notes",optional(draft.getNotes(),1000));replacement.put("packageCount",dispatch.getPackageCount());replacement.put("packageUnit",dispatch.getPackageUnit());replacement.put("correctionRecordedAt",dispatch.getLastCorrectionAt());
        }else if(request.getReplacement()!=null)throw new IllegalArgumentException("撤销时不同时创建替代安排，请分别核对确认。");
        Map<String,Object> impact=payloads.map();impact.put("schema","TRANSPORT_PLAN_ACTION_V1");impact.put("shipmentId",id);impact.put("dispatchId",current.getDispatchId());impact.put("originalHash",initialHash(original));impact.put("previousHash",history.isEmpty()?initialHash(original):history.get(history.size()-1).getActionHash());impact.put("revision",history.size()+1);impact.put("action",request.getAction());impact.put("current",facts(current));impact.put("currentStatus",current.getStatus());impact.put("currentDispatchCount",dispatch.getPackageCount());impact.put("dispatchCorrectionAt",dispatch.getLastCorrectionAt());impact.put("recipient",original.getRecipient());impact.put("deliveryAddress",original.getDeliveryAddress());impact.put("replacement",replacement);impact.put("reason",request.getReason().trim());impact.put("blockers",blocked);
        return Map.of("impact",impact,"impactHash",payloads.requestHash(impact),"canConfirm",blocked.isEmpty());
    }
    private List<String> blockers(Shipment current,String action){List<String> result=new ArrayList<>();if("CANCEL".equals(action)?!"PLANNED".equals(current.getStatus()):!"CANCELLED".equals(current.getStatus()))result.add("CANCEL".equals(action)?"只有待发运安排可撤销，已撤销或已发运不能重复办理。":"须先撤销原安排，再确认替代版本。");if(plans.eventCount(current.getId())>0)result.add("已有实际运输事实，不能撤销或替代待发运安排。");if(plans.linkedCount(current.getDispatchId())>0)result.add("已有逐件包装码关联，不能静默改变履约安排。");if(plans.frozenCount(current.getDispatchId())>0)result.add("已有冻结流转证据，须另行处理证据版本。");if("REPLACE".equals(action)&&sales.dispatch(current.getDispatchId()).getPackageCount()<=0)result.add("本笔当前有效出库为零，不能建立新安排。");return result;}
    private Shipment required(Long id){Shipment row=transport.shipment(id);if(row==null)throw new IllegalArgumentException("运输档案不存在。");AccessContext.checkOrganization(row.getOrganizationId());return row;}
    private Map<String,Object> facts(Shipment row){Map<String,Object> f=payloads.map();f.put("shipmentCode",row.getShipmentCode());f.put("carrier",row.getCarrier());f.put("trackingReference",row.getTrackingReference());f.put("driverName",row.getDriverName());f.put("driverContact",row.getDriverContact());f.put("notes",row.getNotes());f.put("packageCount",row.getPackageCount());f.put("packageUnit",row.getPackageUnit());f.put("correctionRecordedAt",row.getCorrectionRecordedAt());return f;}
    private Map<String,Object> facts(TransportPlanAction row){Map<String,Object> f=payloads.map();f.put("shipmentCode",row.getShipmentCode());f.put("carrier",row.getCarrier());f.put("trackingReference",row.getTrackingReference());f.put("driverName",row.getDriverName());f.put("driverContact",row.getDriverContact());f.put("notes",row.getNotes());f.put("packageCount",row.getPackageCount());f.put("packageUnit",row.getPackageUnit());f.put("correctionRecordedAt",row.getCorrectionRecordedAt());return f;}
    private String initialHash(Shipment original){Map<String,Object> value=payloads.map();value.put("schema","TRANSPORT_PLAN_ORIGIN_V1");value.put("shipmentId",original.getId());value.put("dispatchId",original.getDispatchId());value.put("facts",facts(original));value.put("recipient",original.getRecipient());value.put("deliveryAddress",original.getDeliveryAddress());value.put("recordedBy",original.getRecordedBy());value.put("recordedAt",original.getRecordedAt());return payloads.requestHash(value);}
    private Map<String,Object> body(TransportPlanAction row){Map<String,Object> f=payloads.map();f.put("shipmentId",row.getShipmentId());f.put("revision",row.getRevision());f.put("action",row.getAction());f.put("reason",row.getReason());f.put("plan",facts(row));f.put("impactHash",row.getImpactHash());f.put("previousHash",row.getPreviousHash());f.put("recordedBy",row.getRecordedBy());f.put("recordedAt",row.getRecordedAt());f.put("requestKey",row.getRequestKey());f.put("requestHash",row.getRequestHash());return f;}
    private void validate(Shipment original,Shipment current,List<TransportPlanAction> history){
        if(original==null||history.size()>100)throw new IllegalArgumentException("原始安排快照缺失或历史超限。");String previous=initialHash(original),state="PLANNED";int revision=0;Map<String,Object> expected=facts(original);
        for(TransportPlanAction row:history){if(!current.getId().equals(row.getShipmentId())||row.getRevision()!=++revision||!previous.equals(row.getPreviousHash())||!payloads.payloadHash(row.getImpactJson()).equals(row.getImpactHash())||!payloads.requestHash(body(row)).equals(row.getActionHash()))throw new IllegalArgumentException("安排历史版本或摘要不一致，不能覆盖原记录。");if("CANCEL".equals(row.getAction())&&"PLANNED".equals(state))state="CANCELLED";else if("REPLACE".equals(row.getAction())&&"CANCELLED".equals(state)&&row.getPackageCount()!=null&&row.getPackageCount()>0){state="PLANNED";expected=facts(row);}else throw new IllegalArgumentException("安排历史状态顺序不一致。");previous=row.getActionHash();}
        if(!payloads.canonicalJson(expected).equals(payloads.canonicalJson(facts(current)))||("CANCELLED".equals(state)?!"CANCELLED".equals(current.getStatus()):!Set.of("PLANNED","IN_TRANSIT","DELIVERED","DELIVERY_ISSUE").contains(current.getStatus())))throw new IllegalArgumentException("当前安排与完整历史不一致。");
    }
    private String text(String value,int max,String name){if(value==null||value.trim().isEmpty()||value.trim().length()>max)throw new IllegalArgumentException("请填写有效的"+name+"。");return value.trim();}
    private String optional(String value,int max){return value==null||value.trim().isEmpty()?"":text(value,max,"选填内容");}
    private void key(String value){if(value==null||!value.matches("[A-Za-z0-9-]{36}"))throw new IllegalArgumentException("确认标识无效。");}
}
