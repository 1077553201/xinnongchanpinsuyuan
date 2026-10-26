package org.nongchanpin.service;

import java.util.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.entity.*;
import org.springframework.stereotype.Service;

/** Only the most recent ordinary IN/OUT of a warehouse may be adjusted without fulfillment dependencies. */
@Service
public class MovementCorrectionService extends InventoryCorrectionService {
    private final MovementCorrectionMapper movements;
    public MovementCorrectionService(InventoryMapper inventory,LossCorrectionMapper dependencies,PayloadService payloads,AuditService audit,MovementCorrectionMapper movements){super(inventory,dependencies,payloads,audit);this.movements=movements;}
    @Override protected List<LossCorrectionEntity> history(Long id){return movements.history(id);}
    @Override protected LossCorrectionEntity byKey(String key){return movements.byKey(key);}
    @Override protected void insert(LossCorrectionEntity row){movements.insert(row);}
    @Override protected void allow(InventoryMovementEntity row){if(!Set.of("IN","OUT").contains(row.getKind()))throw new IllegalArgumentException("本入口仅处理普通入库与出库；损耗请从原损耗更正入口办理。");}
    @Override protected long delta(InventoryMovementEntity original,int previous,int count){return "IN".equals(original.getKind())?(long)count-previous:(long)previous-count;}
    @Override protected String schema(){return "INVENTORY_MOVEMENT_CORRECTION_V1";}
    @Override protected List<String> blockers(InventoryMovementEntity original){
        List<String> reasons=new ArrayList<>();
        if(!original.getId().equals(movements.latestMovement(original.getPackingId(),original.getWarehouseId())))reasons.add("本仓本包装已有后续流水，须先核对下游和冲销顺序，本轮不直接调整。");
        if(movements.salesDispatchCount(original.getId())>0)reasons.add("本单已关联销售出库，须通过履约更正流程处理，不能改普通库存单。");
        if(movements.orderLineCount(original.getPackingId())>0)reasons.add("同包装来源已有销售订单（含已停止或已完成），须先核对履约，本轮不直接调整。");
        if(movements.linkedCodeCount(original.getPackingId())>0)reasons.add("同来源包装码已有逐件出库关联，不能通过普通数量更正改变履约库存。");
        return reasons;
    }
    @Override protected void quantities(InventoryMovementEntity original,InventorySource source,int previous,int count,long before,long after){
        long change=delta(original,previous,count),received=source.getReceivedCount()+("IN".equals(original.getKind())?change:0),available=source.getAvailableCount()+change;
        if(before<0||after<0||received<0||received>source.getPackageCount()||available<0||available>received||after>received)throw new IllegalArgumentException("调整后余额不足、累计入库超过实际包装或库存超过有效入库，未执行调整。");
    }
    @Override protected void extras(Map<String,Object> impact,InventoryMovementEntity original,InventorySource source,int previous,int count){
        long change=delta(original,previous,count);impact.put("kind",original.getKind());impact.put("packingCount",source.getPackageCount());impact.put("receivedBefore",source.getReceivedCount());impact.put("receivedAfter",source.getReceivedCount()+("IN".equals(original.getKind())?change:0));impact.put("availableBefore",source.getAvailableCount());impact.put("availableAfter",source.getAvailableCount()+change);
    }
}
