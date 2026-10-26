package org.nongchanpin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class SalesReplacementService {
    private final SalesMapper sales;
    private final SalesReplacementMapper records;
    private final InventoryMapper inventory;
    private final SalesService operations;
    private final PayloadService payloads;
    private final AuditService audit;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id){
        SalesOrderEntity order=required(id);List<SalesReplacementEntity> relations=records.relations(id);
        for(SalesReplacementEntity relation:relations)validate(relation);
        long count=records.dispatchCount(id);
        return Map.of("order",order,"lines",sales.lines(id),"relations",relations,"dispatchCount",count,"dependencies",records.dependencies(id),"dependenciesTruncated",count>200,"blockers",blockers(order,count));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(Long id,@Valid SalesReplacementRequests.Draft request){return candidate(id,request);}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> confirm(Long id,@Valid SalesReplacementRequests.Confirm request){
        AccessContext.write();required(id);sales.lockOrder(id);
        key(request.getRequestKey());String requestHash=payloads.requestHash(Map.of("actor",AccessContext.require().getId(),"orderId",id,"request",request));
        SalesReplacementEntity replay=records.byKey(request.getRequestKey());
        if(replay!=null){validate(replay);if(!id.equals(replay.getOriginalOrderId())||!requestHash.equals(replay.getRequestHash()))throw new IllegalArgumentException("确认标识已用于其他订单更正内容。");return detail(id);}
        for(Long packing:sales.lines(id).stream().map(SalesLineEntity::getPackingId).distinct().sorted().collect(java.util.stream.Collectors.toList()))inventory.lockPacking(packing);
        Map<String,Object> preview=candidate(id,request);
        if(Boolean.FALSE.equals(preview.get("canConfirm")))throw new IllegalArgumentException("原订单已有出库、已停止或已存在替代关系，不能通过本入口更正。");
        if(!preview.get("impactHash").equals(request.getExpectedImpactHash()))throw new IllegalArgumentException("原订单、库存参考或替代内容已变化，请重新预览核对。");
        String frozenImpact=payloads.canonicalJson(preview.get("impact"));
        GrowingRequests.Reason stop=new GrowingRequests.Reason();stop.setReason("更正替代为 "+request.getReplacement().getOrderCode()+"："+request.getReason().trim());
        operations.close(id,stop);
        SalesOrderEntity replacement=operations.create(request.getReplacement());
        if(id.equals(replacement.getId())||!"OPEN".equals(replacement.getStatus())||records.dispatchCount(replacement.getId())!=0)throw new IllegalArgumentException("替代单状态异常，原单与替代操作均已回滚。");
        SalesReplacementEntity row=new SalesReplacementEntity();row.setOrganizationId(required(id).getOrganizationId());row.setOriginalOrderId(id);row.setReplacementOrderId(replacement.getId());row.setReason(request.getReason().trim());
        row.setImpactJson(frozenImpact);row.setImpactHash((String)preview.get("impactHash"));row.setRecordedBy(AccessContext.require().getId());row.setRecordedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));row.setRequestKey(request.getRequestKey());row.setRequestHash(requestHash);row.setRecordHash(payloads.requestHash(recordBody(row)));records.insert(row);
        audit.record("SALES_REPLACEMENT","SALES_ORDER",id,"原单 #"+id+" → 替代单 #"+replacement.getId()+" / "+row.getRecordHash());return detail(id);
    }
    private Map<String,Object> candidate(Long id,SalesReplacementRequests.Draft request){
        SalesOrderEntity order=required(id);SalesRequests.Create draft=request.getReplacement();
        if(draft==null||!order.getOrganizationId().equals(draft.getOrganizationId()))throw new IllegalArgumentException("替代单必须属于原单同一机构。");
        text(request.getReason(),800,"更正原因");key(draft.getRequestKey());text(draft.getCustomerName(),128,"客户名称");text(draft.getDeliveryAddress(),500,"交付地址");
        if(draft.getOrderCode()==null||!draft.getOrderCode().matches("[A-Za-z0-9_-]{1,64}")||draft.getOrderedOn()==null||draft.getOrderedOn().isAfter(LocalDate.now()))throw new IllegalArgumentException("请核对替代订单编码与实际下单日期。");
        if(draft.getLines()==null||draft.getLines().isEmpty()||draft.getLines().size()>50)throw new IllegalArgumentException("请保留原来源明细，最多 50 条。");
        List<SalesLineEntity> original=sales.lines(id);Map<String,SalesLineEntity> pairs=new HashMap<>();for(SalesLineEntity line:original)pairs.put(pair(line.getPackingId(),line.getWarehouseId()),line);
        if(pairs.size()!=original.size()||original.size()!=draft.getLines().size())throw new IllegalArgumentException("本轮只更正原来源的数量与单价，不能新增、删除或替换来源仓库。");
        List<Map<String,Object>> proposed=new ArrayList<>();Set<String> seen=new HashSet<>();Map<Long,Long> totals=new HashMap<>();BigDecimal amount=new BigDecimal("0.00");
        for(SalesRequests.Line line:draft.getLines()){
            String pair=pair(line.getPackingId(),line.getWarehouseId());SalesLineEntity old=pairs.get(pair);
            if(old==null||!seen.add(pair))throw new IllegalArgumentException("请保留每条原包装与仓库，不能重复明细。");
            InventorySource source=inventory.source(line.getPackingId());WarehouseEntity warehouse=inventory.warehouse(line.getWarehouseId());
            if(source==null||warehouse==null||!order.getOrganizationId().equals(source.getOrganizationId())||!order.getOrganizationId().equals(warehouse.getOrganizationId()))throw new IllegalArgumentException("原包装或仓库所属机构异常。");
            if(line.getPackageCount()==null||line.getPackageCount()<1||line.getPackageCount()>1000000||totals.merge(source.getId(),(long)line.getPackageCount(),Long::sum)>source.getPackageCount())throw new IllegalArgumentException("替代数量超过实际包装或不是有效完整件数。");
            if(line.getUnitPrice()==null||line.getUnitPrice().signum()<0)throw new IllegalArgumentException("请填写非负人民币单价。");
            BigDecimal price;try{price=line.getUnitPrice().setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new IllegalArgumentException("单价最多两位小数，不自动舍入。");}
            BigDecimal total=price.multiply(BigDecimal.valueOf(line.getPackageCount()));amount=amount.add(total);
            Map<String,Object> row=payloads.map();row.put("packingId",line.getPackingId());row.put("warehouseId",line.getWarehouseId());row.put("packingCode",source.getPackingCode());row.put("warehouseName",warehouse.getWarehouseName());row.put("productName",source.getProductName());row.put("variety",source.getVariety());row.put("packageUnit",source.getPackageUnit());row.put("netWeightKg",source.getNetWeightKg());row.put("packageCount",line.getPackageCount());row.put("unitPrice",price.toPlainString());row.put("lineAmount",total.toPlainString());row.put("availableCount",inventory.balance(source.getId(),line.getWarehouseId()));proposed.add(row);
        }
        long count=records.dispatchCount(id);List<String> blocked=new ArrayList<>(blockers(order,count));
        if(records.codeCount(draft.getOrderCode())>0)blocked.add("替代订单编码已存在，请更换编码。");
        if(sales.orderByRequest(draft.getRequestKey())!=null)blocked.add("新订单确认标识已有使用记录，请返回重新生成替代预览。");
        Map<String,Object> impact=payloads.map();impact.put("schema","SALES_ORDER_REPLACEMENT_V1");impact.put("original",order);impact.put("originalLines",original);impact.put("dispatchCount",count);impact.put("dependencies",records.dependencies(id));impact.put("reason",request.getReason().trim());impact.put("replacement",draft);impact.put("proposedLines",proposed);impact.put("proposedAmount",amount.toPlainString());impact.put("blockers",blocked);
        return Map.of("impact",impact,"impactHash",payloads.requestHash(impact),"canConfirm",blocked.isEmpty());
    }
    private List<String> blockers(SalesOrderEntity order,long count){List<String> result=new ArrayList<>();if(!"OPEN".equals(order.getStatus()))result.add("仅未出库且仍待出库的订单可停止并替代；已停止或已履约订单须另行处理。");if(count>0)result.add("本单存在实际销售出库，库存、运输和逐件交付不能通过订单替代改写。");if(records.outgoing(order.getId())!=null)result.add("原单已有替代单，请查看既有关系，不能重复替代。");return result;}
    private SalesOrderEntity required(Long id){SalesOrderEntity row=sales.order(id);if(row==null)throw new IllegalArgumentException("原销售订单不存在。");AccessContext.checkOrganization(row.getOrganizationId());return row;}
    private void validate(SalesReplacementEntity row){AccessContext.checkOrganization(row.getOrganizationId());if(!payloads.payloadHash(row.getImpactJson()).equals(row.getImpactHash())||!payloads.requestHash(recordBody(row)).equals(row.getRecordHash()))throw new IllegalArgumentException("订单替代留档摘要不一致，不能覆盖历史。");}
    private Map<String,Object> recordBody(SalesReplacementEntity row){Map<String,Object> value=payloads.map();value.put("schema","SALES_ORDER_REPLACEMENT_RECORD_V1");value.put("organizationId",row.getOrganizationId());value.put("originalOrderId",row.getOriginalOrderId());value.put("replacementOrderId",row.getReplacementOrderId());value.put("reason",row.getReason());value.put("impactHash",row.getImpactHash());value.put("recordedBy",row.getRecordedBy());value.put("recordedAt",row.getRecordedAt());value.put("requestKey",row.getRequestKey());value.put("requestHash",row.getRequestHash());return value;}
    private String pair(Long packing,Long warehouse){return packing+":"+warehouse;}
    private String text(String value,int max,String label){if(value==null||value.trim().isEmpty()||value.trim().length()>max)throw new IllegalArgumentException("请填写有效的"+label+"。");return value.trim();}
    private void key(String value){if(value==null||!value.matches("[A-Za-z0-9-]{36}"))throw new IllegalArgumentException("确认标识无效，请返回重新预览。");}
}
