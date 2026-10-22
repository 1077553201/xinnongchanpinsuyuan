package org.nongchanpin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.*;
import org.nongchanpin.model.entity.AgentModels.Operation;
import org.nongchanpin.model.entity.PlantingCycleEntity;
import org.nongchanpin.model.entity.TransportModels.Shipment;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentTools {
    private final ObjectMapper json;
    private final Validator validator;
    private final GrowingAccess growing;
    private final ProductionMapper production;
    private final TransportMapper transports;
    private final HarvestService harvests;
    private final TransportService transport;
    private final PayloadService hashes;
    private static final Set<String> HARVEST_FIELDS=Set.of("lotCode","harvestedAt","quantity","unit","performerName","notes");
    private static final Set<String> TRANSPORT_FIELDS=Set.of("kind","occurredAt","location","performerName","description","reference","acceptedCount","rejectedCount");

    public Map<String,Object> context(String search) {
        String term=search==null?"":search.trim();if(term.length()>128)throw new IllegalArgumentException("查询词过长。");
        return Map.of("cycles",production.agentCycles(AccessContext.organization(),term),"shipments",transports.agentShipments(AccessContext.organization(),term),"limit",50);
    }
    public String key(Long report,String operation){return UUID.nameUUIDFromBytes(("agent:"+report+":"+operation).getBytes(StandardCharsets.UTF_8)).toString();}
    private Object request(Operation operation,String key) {
        ObjectNode fields=operation.getFields().deepCopy();fields.put("requestKey",key);
        Class<?> type="HARVEST".equals(operation.getKind())?HarvestRequests.Harvest.class:TransportRequests.Event.class;
        try {return json.treeToValue(fields,type);}catch(Exception e){throw new IllegalArgumentException("字段类型或时间格式不正确，请补齐整数件数及具体发生时间。");}
    }
    public Operation prepare(Operation operation) {
        if(operation.getId()==null || !operation.getId().matches("[a-f0-9-]{36}") || operation.getKind()==null || !Set.of("HARVEST","TRANSPORT_EVENT","NOTE").contains(operation.getKind()) || operation.getQuote()==null || operation.getFields()==null || operation.getFields().toString().length()>16000) throw new IllegalArgumentException("请核对操作类型、来源和字段长度。");
        operation.setIssues(new ArrayList<>());operation.setContextHash(null);
        if("NOTE".equals(operation.getKind())) {operation.getFields().removeAll();operation.getIssues().add("待办事项：首版不执行此项，请使用对应业务页面核对办理。");return operation;}
        Set<String> allowed="HARVEST".equals(operation.getKind())?HARVEST_FIELDS:TRANSPORT_FIELDS;
        operation.getFields().fieldNames().forEachRemaining(field->{if(!allowed.contains(field))throw new IllegalArgumentException("操作包含不支持的字段："+field);});
        try { Object request=request(operation,"00000000-0000-4000-8000-000000000000");validator.validate(request).forEach(v->operation.getIssues().add("请填写或核对"+label(v.getPropertyPath().toString())+"。")); }
        catch(IllegalArgumentException e){operation.getIssues().add(e.getMessage());}
        if(operation.getTargetId()==null || operation.getTargetId()<1)operation.getIssues().add("请选择当前权限内的明确业务来源。");
        else try {operation.setContextHash(snapshot(operation,false));}catch(RuntimeException e){operation.getIssues().add("来源不存在、无权限或状态已变化，请重新选择。");}
        return operation;
    }
    private String label(String field) {
        Map<String,String> labels=Map.ofEntries(Map.entry("lotCode","采收批次编码"),Map.entry("harvestedAt","实际采收时间"),Map.entry("quantity","采收数量"),Map.entry("unit","数量单位"),Map.entry("performerName","现场人员"),Map.entry("notes","说明长度"),Map.entry("kind","运输事实类型"),Map.entry("occurredAt","实际发生时间"),Map.entry("location","实际地点"),Map.entry("description","实际情况说明"),Map.entry("acceptedCount","实收件数"),Map.entry("rejectedCount","拒收件数"),Map.entry("reference","凭证引用"));
        return labels.getOrDefault(field,"字段内容");
    }
    private String snapshot(Operation operation,boolean lock) {
        if("HARVEST".equals(operation.getKind())) {
            PlantingCycleEntity c=growing.cycle(operation.getTargetId(),lock);growing.active(c);
            return hashes.requestHash(Map.of("cycle",c.getId(),"plot",c.getPlotId(),"status",c.getStatus(),"plantedOn",c.getPlantedOn(),"product",c.getProductId(),"variety",c.getVariety()));
        }
        Long org=transports.organization(operation.getTargetId());if(org==null)throw new IllegalArgumentException("运输单不存在。");AccessContext.checkOrganization(org);
        if(lock)transports.lock(operation.getTargetId());
        Shipment s=transports.shipment(operation.getTargetId());if(!List.of("PLANNED","IN_TRANSIT").contains(s.getStatus()))throw new IllegalArgumentException("运输单已结束。");
        Object latest=transports.latest(s.getId());
        return hashes.requestHash(Map.of("id",s.getId(),"status",s.getStatus(),"count",s.getPackageCount(),"latest",latest==null?"NONE":latest));
    }
    public Long execute(Long report,Operation operation) {
        if(operation.getContextHash()==null || !operation.getContextHash().equals(snapshot(operation,true)))throw new IllegalArgumentException("来源状态已变化，请保存新版本并重新确认。");
        Object request=request(operation,key(report,operation.getId()));
        if("HARVEST".equals(operation.getKind()))return harvests.create(operation.getTargetId(),(HarvestRequests.Harvest)request).getId();
        if("TRANSPORT_EVENT".equals(operation.getKind()))return transport.record(operation.getTargetId(),(TransportRequests.Event)request).getId();
        throw new IllegalArgumentException("此项尚未接入执行工具。");
    }
}
