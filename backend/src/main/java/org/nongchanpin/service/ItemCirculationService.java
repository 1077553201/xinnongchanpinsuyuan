package org.nongchanpin.service;

import java.util.*;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.ItemCirculationRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.ItemCirculationModels.*;
import org.nongchanpin.model.entity.TransportModels.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

/** Explicit package identity allocation; never repeats the original inventory movement. */
@Service
@Validated
@RequiredArgsConstructor
public class ItemCirculationService {
    private final ItemCirculationMapper items;
    private final SalesMapper sales;
    private final PackingTraceMapper packing;
    private final TransportMapper transport;
    private final PayloadService payloads;
    private final AuditService audit;
    private final RejectionHandlingService rejections;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long dispatchId,String search,String mode,int page) {
        SalesDispatchEntity dispatch=require(dispatchId);
        SalesLineEntity line=sales.line(dispatch.getLineId());
        String term=search==null?"":search.trim();
        if(term.length()>128 || !List.of("ALL","AVAILABLE","ASSIGNED").contains(mode) || page<1) throw new IllegalArgumentException("请核对包装码筛选与分页参数。");
        long total=items.count(line.getPackingId(),dispatchId,term,mode),current=Math.min((long)page,Math.max(1,(total+19)/20));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("dispatch",dispatch);result.put("packingCode",line.getPackingCode());
        result.put("linkedCount",items.linkedCount(dispatchId));
        result.put("acceptedCount",items.receiptCount(dispatchId,"ACCEPTED"));result.put("rejectedCount",items.receiptCount(dispatchId,"REJECTED"));
        Shipment shipment=transport.byDispatch(dispatchId);
        Event receipt=shipment==null?null:transport.receipt(shipment.getId());
        result.put("receipt",receipt==null?null:Map.of("acceptedCount",receipt.getAcceptedCount(),"rejectedCount",receipt.getRejectedCount(),"occurredAt",receipt.getOccurredAt()));
        result.put("rows",items.items(line.getPackingId(),dispatchId,term,mode,(current-1)*20));result.put("total",total);result.put("page",current);result.put("pageSize",20);
        return result;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> link(Long dispatchId,@Valid ItemCirculationRequests.Link request) {
        AccessContext.write();SalesDispatchEntity dispatch=require(dispatchId);
        SalesLineEntity line=sales.line(dispatch.getLineId());
        // All dispatches of this packaging batch share one lock, so a code cannot be allocated twice.
        packing.lock(line.getPackingId());
        dispatch=require(dispatchId);
        String hash=hash(dispatchId,"LINK",request.getItemIds(),null);
        if(replay(dispatchId,request.getRequestKey(),hash))return Map.of("savedCount",request.getItemIds().size());
        Shipment arrangement=transport.byDispatch(dispatchId);if(arrangement!=null&&"CANCELLED".equals(arrangement.getStatus()))throw new IllegalArgumentException("运输安排已撤销，请先确认替代安排，再关联包装码。");
        List<Long> selected=validateIds(request.getItemIds());
        if(items.linkedCount(dispatchId)+selected.size()>dispatch.getPackageCount())throw new IllegalArgumentException("所选包装码超过本笔实际销售出库尚未关联的件数。");
        for(Long id:selected){Item item=items.item(line.getPackingId(),id);if(item==null)throw new IllegalArgumentException("所选码不属于本笔出库的包装来源。");if(item.getDispatchId()!=null)throw new IllegalArgumentException("所选码已关联出库，请刷新核对；不能重复发出同一件包装。");}
        operation(dispatchId,request.getRequestKey(),hash);
        for(Long id:selected)items.link(id,dispatchId,request.getRequestKey(),AccessContext.require().getId());
        audit.record("ITEM_DISPATCH_LINK","SALES_DISPATCH",dispatchId,"包装码 "+selected);
        return Map.of("savedCount",selected.size());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> receive(Long dispatchId,@Valid ItemCirculationRequests.Receipt request) {
        AccessContext.write();SalesDispatchEntity dispatch=require(dispatchId);
        SalesLineEntity line=sales.line(dispatch.getLineId());packing.lock(line.getPackingId());
        String hash=hash(dispatchId,"RECEIPT",request.getItemIds(),request.getOutcome());
        if(replay(dispatchId,request.getRequestKey(),hash))return Map.of("savedCount",request.getItemIds().size());
        List<Long> selected=validateIds(request.getItemIds());
        if(!List.of("ACCEPTED","REJECTED").contains(request.getOutcome()))throw new IllegalArgumentException("请选择实收或拒收结果。");
        Shipment shipment=transport.byDispatch(dispatchId);if(shipment==null)throw new IllegalArgumentException("本笔出库尚无运输单与收货记录。");
        transport.lock(shipment.getId());Event receipt=transport.receipt(shipment.getId());
        if(receipt==null)throw new IllegalArgumentException("请先登记运输单的实际收货汇总，再逐件核对包装码。");
        int limit="ACCEPTED".equals(request.getOutcome())?receipt.getAcceptedCount():receipt.getRejectedCount();
        if(items.receiptCount(dispatchId,request.getOutcome())+selected.size()>limit)throw new IllegalArgumentException("本次逐件结果超过运输单对应的实收或拒收总件数。");
        for(Long id:selected){Item item=items.item(line.getPackingId(),id);if(item==null || !dispatchId.equals(item.getDispatchId()))throw new IllegalArgumentException("所选码尚未关联本笔出库。");if(item.getReceiptOutcome()!=null)throw new IllegalArgumentException("所选码已有收货结果，不能覆盖原记录。");}
        operation(dispatchId,request.getRequestKey(),hash);
        for(Long id:selected)if(items.receive(id,dispatchId,receipt.getId(),request.getOutcome(),request.getRequestKey(),AccessContext.require().getId())!=1)throw new IllegalStateException("包装码状态已变化，请刷新核对。");
        audit.record("ITEM_RECEIPT","SALES_DISPATCH",dispatchId,request.getOutcome()+" 包装码 "+selected+" / 收货事件 "+receipt.getId());
        return Map.of("savedCount",selected.size());
    }

    /** Public whitelist: no customer, address, contacts, operator names, notes, or references. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicView(Long itemId) {
        Item link=items.publicLink(itemId);if(link==null)return null;
        SalesDispatchEntity dispatch=sales.dispatch(link.getDispatchId());
        Map<String,Object> result=new LinkedHashMap<>();result.put("chainStatus","NOT_SUBMITTED");
        result.put("dispatchedAt",dispatch.getOccurredAt());result.put("linkedAt",link.getLinkedAt());
        result.put("receiptOutcome",link.getReceiptOutcome());result.put("receiptRecordedAt",link.getReceiptAt());
        Shipment shipment=transport.byDispatch(link.getDispatchId());
        result.put("transportStatus",shipment==null?"NOT_ARRANGED":shipment.getStatus());
        // Receipt aggregates are not individual receipt evidence; expose only the explicit disposition.
        List<Map<String,Object>> events=new ArrayList<>();
        List<Event> recent=shipment==null?List.of():transport.publicEvents(shipment.getId());
        result.put("eventsTruncated",recent.size()>200);
        recent=new ArrayList<>(recent.subList(0,Math.min(200,recent.size())));Collections.reverse(recent);
        if(shipment!=null)for(Event e:recent){
            if("RECEIVE".equals(e.getKind()) && link.getReceiptOutcome()==null)continue;
            events.add(Map.of("kind",e.getKind(),"occurredAt",e.getOccurredAt()));
        }
        result.put("events",events);Map<String,Object> handling=rejections.publicView(itemId);if(handling!=null)result.put("rejectionHandling",handling);return result;
    }

    private SalesDispatchEntity require(Long id){SalesDispatchEntity dispatch=sales.dispatch(id);if(dispatch==null)throw new IllegalArgumentException("销售出库记录不存在。");SalesOrderEntity order=sales.order(sales.lineOrderId(dispatch.getLineId()));AccessContext.checkOrganization(order.getOrganizationId());return dispatch;}
    private List<Long> validateIds(List<Long> ids){if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id->id==null || id<=0) || new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("每次请选择 1–100 个不同的完整包装码。");return ids;}
    private String hash(Long dispatch,String kind,List<Long> ids,String outcome){Map<String,Object> value=payloads.map();value.put("actor",AccessContext.require().getId());value.put("dispatch",dispatch);value.put("kind",kind);value.put("items",new TreeSet<>(validateIds(ids)));value.put("outcome",outcome);return payloads.requestHash(value);}
    private boolean replay(Long dispatch,String key,String hash){Operation old=items.operation(key);if(old==null)return false;if(!dispatch.equals(old.getDispatchId()) || !hash.equals(old.getRequestHash()))throw new IllegalArgumentException("确认标识已用于其他操作，请刷新核对。");return true;}
    private void operation(Long dispatch,String key,String hash){Operation record=new Operation();record.setDispatchId(dispatch);record.setRequestKey(key);record.setRequestHash(hash);record.setRecordedBy(AccessContext.require().getId());items.insertOperation(record);}
}
