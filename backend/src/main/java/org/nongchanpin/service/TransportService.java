package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.util.*;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.TransportRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.TransportModels.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class TransportService {
    private final TransportMapper transport;
    private final SalesMapper sales;
    private final PayloadService payloads;
    private final AuditService audit;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(String search,String status,int page,int size) {
        String term=text(search),state=text(status);
        if(term.length()>128 || !List.of("","PLANNED","IN_TRANSIT","DELIVERED","DELIVERY_ISSUE").contains(state) || page<1 || size<1 || size>100) throw new IllegalArgumentException("请核对搜索、状态与分页参数。");
        long total=transport.count(AccessContext.organization(),term,state),current=Math.min((long)page,Math.max(1,(total+size-1)/size));
        return Map.of("rows",transport.list(AccessContext.organization(),term,state,(current-1)*size,size),"total",total,"page",current,"pageSize",size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id) { Shipment s=require(id);return Map.of("shipment",s,"events",transport.events(id)); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Shipment create(@Valid TransportRequests.Create request) {
        AccessContext.write(); SalesDispatchEntity d=sales.dispatch(request.getDispatchId());
        if(d==null)throw new IllegalArgumentException("请从实际销售出库记录建立运输单。");
        Long orderId=sales.lineOrderId(d.getLineId()); SalesOrderEntity order=sales.order(orderId);AccessContext.checkOrganization(order.getOrganizationId());
        // 与销售出库共用订单锁；唯一出库关联防止重复建立运输单，不改库存。
        sales.lockOrder(orderId);
        String hash=hash(request.getDispatchId(),request); Shipment previous=transport.byRequest(request.getRequestKey());
        if(previous!=null){same(hash,previous.getRequestHash());return transport.shipment(previous.getId());}
        if(transport.byDispatch(d.getId())!=null)throw new IllegalArgumentException("本笔出库已有运输单，请查看原记录。");
        Shipment s=new Shipment();s.setDispatchId(d.getId());s.setShipmentCode(request.getShipmentCode());s.setCarrier(request.getCarrier().trim());s.setTrackingReference(text(request.getTrackingReference()));
        s.setDriverName(request.getDriverName().trim());s.setDriverContact(text(request.getDriverContact()));s.setRecipient(order.getCustomerName());s.setDeliveryAddress(order.getDeliveryAddress());s.setNotes(text(request.getNotes()));
        s.setRecordedBy(AccessContext.require().getId());s.setRecorderName(AccessContext.require().getDisplayName());s.setRequestKey(request.getRequestKey());s.setRequestHash(hash);
        transport.insert(s);audit.record("TRANSPORT_CREATE","TRANSPORT",s.getId(),s.getShipmentCode());return transport.shipment(s.getId());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Event record(Long id,@Valid TransportRequests.Event request) {
        AccessContext.write();Long org=transport.organization(id);if(org==null)throw new IllegalArgumentException("运输单不存在。");AccessContext.checkOrganization(org);transport.lock(id);
        // 锁前只读取不可变机构字段，首次状态查询放在取得行锁之后。
        Shipment s=transport.shipment(id);
        String hash=hash(id,request);Event previous=transport.eventByRequest(request.getRequestKey());
        if(previous!=null){same(hash,previous.getRequestHash());return previous;}
        if(List.of("DELIVERED","DELIVERY_ISSUE").contains(s.getStatus()))throw new IllegalArgumentException("本运输单已登记收货结果，不能继续追加运输事实。");
        String kind=request.getKind();
        if("PLANNED".equals(s.getStatus()) ? !"DEPART".equals(kind) : "DEPART".equals(kind)) throw new IllegalArgumentException("待发运须先登记实际发运；同单只能登记一次发运。");
        Event latest=transport.latest(id);LocalDateTime floor=latest==null?s.getDispatchedAt():latest.getOccurredAt();
        if(request.getOccurredAt().isBefore(floor) || request.getOccurredAt().isAfter(LocalDateTime.now()))throw new IllegalArgumentException("实际时间不能早于出库或上一条运输事实，也不能晚于当前时间。");
        if("RECEIVE".equals(kind)) {
            if(request.getAcceptedCount()==null || request.getRejectedCount()==null || (long)request.getAcceptedCount()+request.getRejectedCount()!=s.getPackageCount()) throw new IllegalArgumentException("请明确填写实收与拒收件数，合计须等于本笔出库件数。");
        } else if(request.getAcceptedCount()!=null || request.getRejectedCount()!=null)throw new IllegalArgumentException("只有收货结果可以填写实收、拒收件数。");
        Event e=new Event();e.setShipmentId(id);e.setKind(kind);e.setOccurredAt(request.getOccurredAt());e.setLocation(request.getLocation().trim());e.setPerformerName(request.getPerformerName().trim());e.setDescription(request.getDescription().trim());e.setReference(text(request.getReference()));e.setAcceptedCount(request.getAcceptedCount());e.setRejectedCount(request.getRejectedCount());e.setRecordedBy(AccessContext.require().getId());e.setRecorderName(AccessContext.require().getDisplayName());e.setRequestKey(request.getRequestKey());e.setRequestHash(hash);
        transport.insertEvent(e);
        if("DEPART".equals(kind))transport.status(id,"IN_TRANSIT");
        if("RECEIVE".equals(kind))transport.status(id,request.getRejectedCount()==0?"DELIVERED":"DELIVERY_ISSUE");
        audit.record("TRANSPORT_"+kind,"TRANSPORT",id,"事件 #"+e.getId());return e;
    }
    private Shipment require(Long id){Shipment s=transport.shipment(id);if(s==null)throw new IllegalArgumentException("运输单不存在。");AccessContext.checkOrganization(s.getOrganizationId());return s;}
    private String hash(Long id,Object request){Map<String,Object> m=payloads.map();m.put("actor",AccessContext.require().getId());m.put("source",id);m.put("request",request);return payloads.requestHash(m);}
    private void same(String a,String b){if(!a.equals(b))throw new IllegalArgumentException("确认标识已用于其他内容，请刷新核对。");}
    private String text(String s){return s==null?"":s.trim();}
}
