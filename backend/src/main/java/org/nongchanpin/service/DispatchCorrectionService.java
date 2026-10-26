package org.nongchanpin.service;

import java.util.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;

/** Correct only unallocated, unscheduled sales dispatches; preserve the original movement and invoice. */
@Service
public class DispatchCorrectionService extends InventoryCorrectionService {
    private final DispatchCorrectionMapper dispatches;
    private final SalesMapper sales;
    public DispatchCorrectionService(InventoryMapper inventory,LossCorrectionMapper dependencies,PayloadService payloads,AuditService audit,DispatchCorrectionMapper dispatches,SalesMapper sales){super(inventory,dependencies,payloads,audit);this.dispatches=dispatches;this.sales=sales;}
    @Override protected List<LossCorrectionEntity> history(Long id){return dispatches.history(id);}
    @Override protected LossCorrectionEntity byKey(String key){return dispatches.byKey(key);}
    @Override protected void insert(LossCorrectionEntity row){dispatches.insert(row);}
    @Override protected String schema(){return "SALES_DISPATCH_CORRECTION_V1";}
    @Override protected String auditAction(InventoryMovementEntity original){return "SALES_DISPATCH_CORRECTION";}
    @Override protected void allow(InventoryMovementEntity original){
        if(!"OUT".equals(original.getKind())||dispatches.dispatch(original.getId())==null)throw new IllegalArgumentException("本入口只处理销售出库，普通库存单请从原入口办理。");
        SalesOrderEntity order=order(original);AccessContext.checkOrganization(order.getOrganizationId());
        SalesLineEntity line=line(original);if(line==null||!order.getOrganizationId().equals(inventory.source(original.getPackingId()).getOrganizationId())||!original.getPackingId().equals(line.getPackingId())||!original.getWarehouseId().equals(line.getWarehouseId()))throw new IllegalArgumentException("销售明细和原库存来源不一致。");
    }
    @Override protected void lock(InventoryMovementEntity original){sales.lockOrder(order(original).getId());inventory.lockPacking(original.getPackingId());}
    @Override protected List<String> blockers(InventoryMovementEntity original){
        SalesDispatchEntity dispatch=dispatches.dispatch(original.getId());SalesOrderEntity order=order(original);List<String> result=new ArrayList<>();
        if(!Set.of("OPEN","PARTIAL","FULFILLED").contains(order.getStatus()))result.add("原订单已停止或取消，不能通过出库更正重新开启履约。");
        if(!original.getId().equals(dispatches.latestMovement(original.getPackingId(),original.getWarehouseId())))result.add("本仓本包装已有后续流水，须先核对下游，暂不直接调整。");
        if(dispatches.shipmentCount(dispatch.getId())>0)result.add("本笔已有运输安排，即使尚未发运也不能直接更正；请另走运输与履约更正流程。");
        if(dispatches.linkedCount(dispatch.getId())>0)result.add("本笔已有逐件包装码关联，不能覆盖原出库身份与收货事实。");
        if(dispatches.frozenCount(dispatch.getId())>0)result.add("本笔已有冻结流转证据，不能静默改变存证事实。");
        return result;
    }
    @Override protected void quantities(InventoryMovementEntity original,InventorySource source,int previous,int count,long before,long after){
        super.quantities(original,source,previous,count,before,after);SalesLineEntity line=line(original);
        long total=(long)line.getDispatchedCount()-previous+count;
        if(total<0||total>line.getPackageCount())throw new IllegalArgumentException("调整后本明细累计有效出库超过订购数量或不正确，未执行调整。");
    }
    @Override protected void extras(Map<String,Object> impact,InventoryMovementEntity original,InventorySource source,int previous,int count){
        SalesLineEntity line=line(original);SalesOrderEntity order=order(original);long total=(long)line.getDispatchedCount()-previous+count;
        List<SalesLineEntity> lines=sales.lines(order.getId());
        impact.put("dispatchId",dispatches.dispatch(original.getId()).getId());impact.put("orderId",order.getId());impact.put("orderCode",order.getOrderCode());impact.put("orderStatusBefore",order.getStatus());impact.put("orderStatusAfter",status(lines,line.getId(),total));impact.put("lineId",line.getId());impact.put("orderedCount",line.getPackageCount());impact.put("dispatchedBefore",line.getDispatchedCount());impact.put("dispatchedAfter",total);impact.put("remainingBefore",line.getPackageCount()-line.getDispatchedCount());impact.put("remainingAfter",line.getPackageCount()-total);impact.put("orderLines",lines);
    }
    @Override protected void afterInsert(InventoryMovementEntity original,LossCorrectionEntity correction){
        SalesOrderEntity order=order(original);List<SalesLineEntity> lines=sales.lines(order.getId());
        if(lines.isEmpty()||lines.stream().anyMatch(l->l.getDispatchedCount()<0||l.getDispatchedCount()>l.getPackageCount()))throw new IllegalStateException("有效出库与订单数量不一致，调整已回滚。");
        sales.status(order.getId(),status(lines,null,0));
    }
    private String status(List<SalesLineEntity> lines,Long changed,long value){if(lines.isEmpty())throw new IllegalArgumentException("原订单缺少明细。");boolean all=true,any=false;for(SalesLineEntity line:lines){long count=line.getId().equals(changed)?value:line.getDispatchedCount();all&=count==line.getPackageCount();any|=count>0;}return all?"FULFILLED":any?"PARTIAL":"OPEN";}
    private SalesLineEntity line(InventoryMovementEntity original){return sales.line(dispatches.dispatch(original.getId()).getLineId());}
    private SalesOrderEntity order(InventoryMovementEntity original){SalesDispatchEntity dispatch=dispatches.dispatch(original.getId());Long id=sales.lineOrderId(dispatch.getLineId());SalesOrderEntity order=sales.order(id);if(order==null)throw new IllegalArgumentException("原销售订单不存在。");return order;}
}
