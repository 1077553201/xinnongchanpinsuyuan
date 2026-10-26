package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.LossCorrectionRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.transaction.annotation.*;

/** Append-only corrections apply to current stock; original warehouse facts and downstream documents remain intact. */
public abstract class InventoryCorrectionService {
    protected final InventoryMapper inventory;
    protected final LossCorrectionMapper corrections;
    protected final PayloadService payloads;
    protected final AuditService audit;

    protected InventoryCorrectionService(InventoryMapper inventory,LossCorrectionMapper corrections,PayloadService payloads,AuditService audit){this.inventory=inventory;this.corrections=corrections;this.payloads=payloads;this.audit=audit;}
    protected List<LossCorrectionEntity> history(Long id){return corrections.history(id);}
    protected LossCorrectionEntity byKey(String key){return corrections.byKey(key);}
    protected void insert(LossCorrectionEntity row){corrections.insert(row);}
    protected void allow(InventoryMovementEntity row){if(!"LOSS".equals(row.getKind())||row.getSalesOrderId()!=null)throw new IllegalArgumentException("本入口仅支持普通库存损耗单更正。");}
    protected long delta(InventoryMovementEntity original,int previous,int count){return (long)previous-count;}
    protected String schema(){return "INVENTORY_LOSS_CORRECTION_V1";}
    protected List<String> blockers(InventoryMovementEntity original){return List.of();}
    protected void extras(Map<String,Object> impact,InventoryMovementEntity original,InventorySource source,int previous,int count){}
    protected void quantities(InventoryMovementEntity original,InventorySource source,int previous,int count,long before,long after){
        if(before<0||after<0||after>source.getPackageCount()||source.getAvailableCount()+delta(original,previous,count)>source.getReceivedCount())throw new IllegalArgumentException("调整后库存不足或超过实际已入库数量，不能通过更正制造库存。");
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long movementId){
        InventoryMovementEntity original=original(movementId);List<LossCorrectionEntity> history=history(movementId);validate(original,history);
        return Map.of("original",original,"history",history,"effectiveCount",effective(original,history));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(Long movementId,int correctedCount){return candidate(original(movementId),correctedCount);}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> confirm(Long movementId,LossCorrectionRequest request){
        AccessContext.write();InventoryMovementEntity original=original(movementId);
        // All ordinary movements, sales dispatch and corrections serialize on this same packaging row.
        inventory.lockPacking(original.getPackingId());
        if(request.getRequestKey()==null||!request.getRequestKey().matches("[A-Za-z0-9-]{36}"))throw new IllegalArgumentException("确认标识无效。");
        String requestHash=payloads.requestHash(Map.of("actor",AccessContext.require().getId(),"movementId",movementId,"request",request));
        LossCorrectionEntity replay=byKey(request.getRequestKey());
        if(replay!=null){if(!movementId.equals(replay.getMovementId())||!requestHash.equals(replay.getRequestHash()))throw new IllegalArgumentException("确认标识已用于其他更正内容。");return detail(movementId);}
        if(request.getCorrectedCount()==null)throw new IllegalArgumentException("请填写核实后的完整包装件数。");
        Map<String,Object> preview=candidate(original,request.getCorrectedCount());
        if(Boolean.FALSE.equals(preview.get("canConfirm")))throw new IllegalArgumentException(String.join("；",blockers(original)));
        if(!preview.get("impactHash").equals(request.getExpectedImpactHash()))throw new IllegalArgumentException("库存、历史版本或下游关联已变化，请重新预览核对。");
        @SuppressWarnings("unchecked") Map<String,Object> impact=(Map<String,Object>)preview.get("impact");
        LocalDateTime recordedAt=LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),verified=request.getVerifiedAt();
        if(verified==null||verified.isBefore(original.getOccurredAt())||verified.isAfter(recordedAt))throw new IllegalArgumentException("核实时间须在原单据发生后且不晚于当前时间。");
        List<LossCorrectionEntity> history=history(movementId);
        LossCorrectionEntity row=new LossCorrectionEntity();row.setMovementId(movementId);row.setRevision(history.size()+1);
        row.setPreviousCount(effective(original,history));row.setCorrectedCount(request.getCorrectedCount());
        row.setBalanceBefore(((Number)impact.get("balanceBefore")).longValue());row.setBalanceAfter(((Number)impact.get("balanceAfter")).longValue());
        row.setVerifiedAt(verified.truncatedTo(ChronoUnit.SECONDS));row.setPerformerName(text(request.getPerformerName(),128,"核实人员"));row.setReason(text(request.getReason(),1000,"更正原因"));row.setReference(optional(request.getReference()));
        row.setOriginalHash(originalHash(original));row.setPreviousHash(previousHash(original,history));row.setImpactJson(payloads.canonicalJson(impact));row.setImpactHash((String)preview.get("impactHash"));
        row.setRecordedBy(AccessContext.require().getId());row.setRecordedAt(recordedAt);row.setRequestKey(request.getRequestKey());row.setRequestHash(requestHash);
        row.setPayloadJson(payloads.canonicalJson(body(row)));row.setCorrectionHash(payloads.payloadHash(row.getPayloadJson()));insert(row);
        audit.record("LOSS".equals(original.getKind())?"INVENTORY_LOSS_CORRECTION":"INVENTORY_QUANTITY_CORRECTION","INVENTORY_MOVEMENT",movementId,"调整 #"+row.getId()+" / "+row.getPreviousCount()+"→"+row.getCorrectedCount()+" / "+row.getCorrectionHash());return detail(movementId);
    }

    private Map<String,Object> candidate(InventoryMovementEntity original,int count){
        if(count<0||count>1000000)throw new IllegalArgumentException("核实件数须为 0 至 1000000 的整数。");
        List<LossCorrectionEntity> history=history(original.getId());validate(original,history);
        if(history.size()>=100)throw new IllegalArgumentException("本单已达 100 个更正版本，请联系管理员核对。");
        int previous=effective(original,history);if(count==previous)throw new IllegalArgumentException("核实件数与当前有效数量相同，无需更正。");
        InventorySource source=inventory.source(original.getPackingId());
        long before=inventory.balance(original.getPackingId(),original.getWarehouseId()),after=before+delta(original,previous,count);
        quantities(original,source,previous,count,before,after);
        List<Map<String,Object>> orders=corrections.orders(original.getPackingId()),codes=corrections.codes(original.getPackingId()),evidence=corrections.evidence(original.getPackingId());
        if(orders.size()>200||codes.size()>200||evidence.size()>200)throw new IllegalArgumentException("本来源关联超过单类 200 条预览上限，请联系管理员完整核对，未执行调整。");
        Map<String,Object> impact=payloads.map();impact.put("schema",schema());impact.put("movementId",original.getId());impact.put("packingId",source.getId());impact.put("warehouseId",original.getWarehouseId());
        impact.put("originalHash",originalHash(original));impact.put("previousHash",previousHash(original,history));impact.put("revision",history.size()+1);
        impact.put("previousCount",previous);impact.put("correctedCount",count);impact.put("stockDelta",(int)delta(original,previous,count));impact.put("balanceBefore",before);impact.put("balanceAfter",after);impact.put("packageUnit",source.getPackageUnit());impact.put("netWeightKg",source.getNetWeightKg());
        impact.put("positions",inventory.positions(source.getId()));impact.put("orders",orders);impact.put("relatedCodes",codes);impact.put("evidence",evidence);
        extras(impact,original,source,previous,count);
        List<String> blocked=blockers(original);if(!"LOSS".equals(original.getKind()))impact.put("blockers",blocked);
        return Map.of("original",original,"impact",impact,"impactHash",payloads.requestHash(impact),"canConfirm",blocked.isEmpty());
    }
    private InventoryMovementEntity original(Long id){
        InventoryMovementEntity row=inventory.movement(id);if(row==null)throw new IllegalArgumentException("原库存单据不存在。");
        InventorySource source=inventory.source(row.getPackingId());if(source==null)throw new IllegalArgumentException("原包装来源不存在。");AccessContext.checkOrganization(source.getOrganizationId());
        WarehouseEntity warehouse=inventory.warehouse(row.getWarehouseId());if(warehouse==null||!source.getOrganizationId().equals(warehouse.getOrganizationId()))throw new IllegalArgumentException("原仓库和包装所属机构不一致。");
        allow(row);
        if(!source.getPackageUnit().equals(row.getPackageUnit())||source.getNetWeightKg().multiply(java.math.BigDecimal.valueOf(row.getPackageCount())).compareTo(row.getWeightKg())!=0)throw new IllegalArgumentException("原单据规格与来源不一致，请先核对。");return row;
    }
    private String originalHash(InventoryMovementEntity row){Map<String,Object> value=payloads.map();value.put("id",row.getId());value.put("packingId",row.getPackingId());value.put("warehouseId",row.getWarehouseId());value.put("movementCode",row.getMovementCode());value.put("kind",row.getKind());value.put("count",row.getPackageCount());value.put("unit",row.getPackageUnit());value.put("weightKg",row.getWeightKg());value.put("occurredAt",row.getOccurredAt());value.put("performer",row.getPerformerName());value.put("reason",row.getReason());value.put("reference",row.getReference());value.put("recordedBy",row.getRecordedBy());value.put("recordedAt",row.getRecordedAt());return payloads.requestHash(value);}
    private int effective(InventoryMovementEntity original,List<LossCorrectionEntity> history){return history.isEmpty()?original.getPackageCount():history.get(history.size()-1).getCorrectedCount();}
    private String previousHash(InventoryMovementEntity original,List<LossCorrectionEntity> history){return history.isEmpty()?originalHash(original):history.get(history.size()-1).getCorrectionHash();}
    private void validate(InventoryMovementEntity original,List<LossCorrectionEntity> history){
        int count=original.getPackageCount(),revision=0;String hash=originalHash(original);
        if(history.size()>100)throw new IllegalArgumentException("更正历史超过允许范围。");
        for(LossCorrectionEntity row:history){
            if(!original.getId().equals(row.getMovementId())||row.getRevision()!=++revision||row.getPreviousCount()!=count||!originalHash(original).equals(row.getOriginalHash())||!hash.equals(row.getPreviousHash())
                    ||row.getCorrectedCount()<0||row.getCorrectedCount()>1000000||row.getCorrectedCount()==count||row.getBalanceBefore()<0||row.getBalanceAfter()<0||row.getBalanceAfter()!=row.getBalanceBefore()+delta(original,count,row.getCorrectedCount())
                    ||!payloads.payloadHash(row.getImpactJson()).equals(row.getImpactHash())||!payloads.payloadHash(row.getPayloadJson()).equals(row.getCorrectionHash())||!payloads.canonicalJson(body(row)).equals(row.getPayloadJson()))throw new IllegalArgumentException("原单据或更正历史摘要、数量不一致，不能覆盖记录。");
            count=row.getCorrectedCount();hash=row.getCorrectionHash();
        }
    }
    private Map<String,Object> body(LossCorrectionEntity row){Map<String,Object> value=payloads.map();value.put("movementId",row.getMovementId());value.put("revision",row.getRevision());value.put("previousCount",row.getPreviousCount());value.put("correctedCount",row.getCorrectedCount());value.put("balanceBefore",row.getBalanceBefore());value.put("balanceAfter",row.getBalanceAfter());value.put("verifiedAt",row.getVerifiedAt());value.put("performerName",row.getPerformerName());value.put("reason",row.getReason());value.put("reference",row.getReference());value.put("originalHash",row.getOriginalHash());value.put("previousHash",row.getPreviousHash());value.put("impactHash",row.getImpactHash());value.put("actor",row.getRecordedBy());value.put("recordedAt",row.getRecordedAt());value.put("requestKey",row.getRequestKey());value.put("requestHash",row.getRequestHash());return value;}
    private String text(String value,int max,String name){if(value==null||value.trim().isEmpty()||value.trim().length()>max)throw new IllegalArgumentException("请填写有效的"+name+"，最多 "+max+" 字。");return value.trim();}
    private String optional(String value){return value==null||value.trim().isEmpty()?"":text(value,128,"参考单据");}
}
