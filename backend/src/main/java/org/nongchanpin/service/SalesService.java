package org.nongchanpin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class SalesService {
    private final SalesMapper sales;
    private final InventoryMapper inventory;
    private final InventoryService stock;
    private final ProductionMapper production;
    private final PayloadService payloads;
    private final AuditService audit;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(String search,String status,int page,int size) {
        String term=text(search),state=text(status);
        if (term.length()>128 || !List.of("","OPEN","PARTIAL","FULFILLED","CANCELLED","CLOSED").contains(state) || page<1 || size<1 || size>100) throw new IllegalArgumentException("请核对搜索、状态或分页参数。");
        long total=sales.count(AccessContext.organization(),term,state),current=Math.min((long)page,Math.max(1,(total+size-1)/size));
        return Map.of("rows",sales.orders(AccessContext.organization(),term,state,(current-1)*size,size),"total",total,"page",current,"pageSize",size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id) { SalesOrderEntity order=require(id); return Map.of("order",order,"lines",sales.lines(id),"dispatches",sales.dispatches(id)); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SalesOrderEntity create(@Valid SalesRequests.Create request) {
        AccessContext.write(); AccessContext.checkOrganization(request.getOrganizationId());
        Map<String,Object> content=payloads.map(); content.put("actor",AccessContext.require().getId()); content.put("request",request);
        String hash=payloads.requestHash(content);
        SalesOrderEntity existing=sales.orderByRequest(request.getRequestKey());
        if (existing!=null) { same(hash,existing.getRequestHash()); return existing; }
        if (production.activeOrganization(request.getOrganizationId())!=1 || request.getOrderedOn().isAfter(LocalDate.now())) throw new IllegalArgumentException("请选择有效机构，实际下单日期不能晚于今天。");
        // Lock in a stable order so a new fulfillment dependency cannot race a plain stock correction.
        for(Long packingId:request.getLines().stream().map(SalesRequests.Line::getPackingId).distinct().sorted().collect(java.util.stream.Collectors.toList())) {
            InventorySource candidate=inventory.source(packingId);
            if(candidate==null||!request.getOrganizationId().equals(candidate.getOrganizationId()))throw new IllegalArgumentException("订单包装来源不属于本机构。");
            inventory.lockPacking(packingId);
        }
        List<SalesLineEntity> lines=new ArrayList<>(); Set<String> pairs=new HashSet<>(); Map<Long,Long> quantities=new HashMap<>();
        production.lockOrganization(request.getOrganizationId());
        if (production.activeOrganization(request.getOrganizationId()) != 1) throw new IllegalArgumentException("所属机构已停用，请刷新机构选项后重试。");
        BigDecimal total=new BigDecimal("0.00");
        for (SalesRequests.Line input:request.getLines()) {
            InventorySource source=inventory.source(input.getPackingId()); WarehouseEntity warehouse=inventory.warehouse(input.getWarehouseId());
            if (source==null || warehouse==null || !request.getOrganizationId().equals(source.getOrganizationId()) || !request.getOrganizationId().equals(warehouse.getOrganizationId())) throw new IllegalArgumentException("订单明细的包装来源、仓库与订单必须属于同一机构。");
            if (!pairs.add(input.getPackingId()+":"+input.getWarehouseId())) throw new IllegalArgumentException("同一包装与仓库请合并为一条明细。");
            long sum=quantities.merge(input.getPackingId(),(long)input.getPackageCount(),Long::sum);
            if (sum>source.getPackageCount()) throw new IllegalArgumentException("本单对同批包装的订购总量不能超过其实际包装件数。");
            SalesLineEntity line=new SalesLineEntity(); line.setPackingId(source.getId()); line.setWarehouseId(warehouse.getId()); line.setPackingCode(source.getPackingCode()); line.setWarehouseName(warehouse.getWarehouseName());
            line.setProductName(source.getProductName()); line.setVariety(source.getVariety()); line.setPackageUnit(source.getPackageUnit()); line.setNetWeightKg(source.getNetWeightKg()); line.setPackageCount(input.getPackageCount());
            try { line.setUnitPrice(input.getUnitPrice().setScale(2,RoundingMode.UNNECESSARY)); } catch (ArithmeticException exception) { throw new IllegalArgumentException("单价最多保留两位小数，不自动舍入。"); }
            line.setLineAmount(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getPackageCount()))); total=total.add(line.getLineAmount()); lines.add(line);
        }
        SalesOrderEntity order=new SalesOrderEntity(); order.setOrganizationId(request.getOrganizationId()); order.setOrderCode(request.getOrderCode()); order.setOrderedOn(request.getOrderedOn());
        order.setCustomerName(request.getCustomerName().trim()); order.setCustomerContact(text(request.getCustomerContact())); order.setDeliveryAddress(request.getDeliveryAddress().trim()); order.setNotes(text(request.getNotes())); order.setTotalAmount(total);
        order.setRecordedBy(AccessContext.require().getId()); order.setRecorderName(AccessContext.require().getDisplayName()); order.setRequestKey(request.getRequestKey()); order.setRequestHash(hash);
        try { sales.insertOrder(order); }
        catch (org.springframework.dao.DuplicateKeyException exception) {
            SalesOrderEntity replay=sales.orderByRequest(request.getRequestKey());
            if (replay==null) throw exception;
            same(hash,replay.getRequestHash()); return replay;
        }
        for(SalesLineEntity line:lines){line.setOrderId(order.getId()); sales.insertLine(line);}
        audit.record("SALES_CREATE","SALES_ORDER",order.getId(),order.getOrderCode()); return sales.order(order.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SalesDispatchEntity dispatch(Long lineId,@Valid SalesRequests.Dispatch request) {
        AccessContext.write(); Long orderId=sales.lineOrderId(lineId); if(orderId==null) throw new IllegalArgumentException("订单明细不存在。");
        require(orderId); SalesOrderEntity order=sales.lockOrder(orderId);
        // 在取得订单锁之后首次读取数量，避免 MyBatis 会话缓存复用锁前的余量。
        SalesLineEntity line=sales.line(lineId);
        Map<String,Object> content=payloads.map(); content.put("actor",AccessContext.require().getId()); content.put("line",lineId); content.put("request",request); String hash=payloads.requestHash(content);
        SalesDispatchEntity previous=sales.dispatchByRequest(request.getRequestKey()); if(previous!=null){same(hash,previous.getRequestHash());return sales.dispatch(previous.getId());}
        if (!List.of("OPEN","PARTIAL").contains(order.getStatus())) throw new IllegalArgumentException("订单已出库完成或已停止，不能继续登记销售出库。");
        if (request.getPackageCount()>line.getPackageCount()-line.getDispatchedCount()) throw new IllegalArgumentException("出库件数超过本条订单尚未出库的数量。");
        if (request.getOccurredAt().toLocalDate().isBefore(order.getOrderedOn())) throw new IllegalArgumentException("销售出库时间不能早于实际下单日期。");
        InventoryRequests.Movement movement=new InventoryRequests.Movement(); movement.setWarehouseId(line.getWarehouseId()); movement.setMovementCode("SALE-"+request.getRequestKey()); movement.setKind("OUT"); movement.setPackageCount(request.getPackageCount());
        movement.setOccurredAt(request.getOccurredAt()); movement.setPerformerName(request.getPerformerName()); movement.setDestination(order.getCustomerName()); movement.setReason(request.getReason()); movement.setReference(request.getReference()); movement.setRequestKey(request.getRequestKey());
        InventoryMovementEntity saved=stock.record(line.getPackingId(),movement);
        SalesDispatchEntity record=new SalesDispatchEntity(); record.setLineId(lineId); record.setMovementId(saved.getId()); record.setRequestKey(request.getRequestKey()); record.setRequestHash(hash); sales.insertDispatch(record);
        boolean complete=sales.lines(order.getId()).stream().allMatch(item->item.getDispatchedCount().equals(item.getPackageCount())); sales.status(order.getId(),complete?"FULFILLED":"PARTIAL");
        audit.record("SALES_DISPATCH","SALES_ORDER",order.getId(),"明细 "+lineId+" / 库存流水 "+saved.getId()); return sales.dispatch(record.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SalesOrderEntity close(Long id,@Valid GrowingRequests.Reason request) {
        AccessContext.write(); require(id); SalesOrderEntity order=sales.lockOrder(id);
        if (List.of("CANCELLED","CLOSED").contains(order.getStatus())) { if(!request.getReason().trim().equals(order.getCloseReason()))throw new IllegalArgumentException("订单已停止，请刷新核对原说明。"); return order; }
        if ("FULFILLED".equals(order.getStatus())) throw new IllegalArgumentException("订单已全部出库，不能通过停止订单退回库存。");
        order.setStatus("OPEN".equals(order.getStatus())?"CANCELLED":"CLOSED"); order.setCloseReason(request.getReason().trim()); order.setClosedBy(AccessContext.require().getId()); order.setClosedAt(LocalDateTime.now()); sales.close(order);
        audit.record("SALES_CLOSE","SALES_ORDER",id,order.getCloseReason());return sales.order(id);
    }
    private SalesOrderEntity require(Long id) { SalesOrderEntity order=sales.order(id);if(order==null)throw new IllegalArgumentException("销售订单不存在。");AccessContext.checkOrganization(order.getOrganizationId());return order; }
    private void same(String a,String b) { if(!a.equals(b))throw new IllegalArgumentException("确认标识已用于其他内容，请刷新核对。"); }
    private String text(String value) { return value==null?"":value.trim(); }
}
