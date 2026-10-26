package org.nongchanpin.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.InventoryMapper;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.dto.InventoryRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryMapper inventory;
    private final ProductionMapper production;
    private final GrowingAccess access;
    private final PayloadService payloads;
    private final AuditService audit;

    public List<WarehouseEntity> warehouses() { return inventory.warehouses(AccessContext.organization()); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> warehouseStock(Long id,String search,boolean includeEmpty,int page,int pageSize) {
        WarehouseEntity warehouse=inventory.warehouse(id);
        if (warehouse==null) throw new IllegalArgumentException("仓库不存在。");
        AccessContext.checkOrganization(warehouse.getOrganizationId());
        String term=text(search);
        if (term.length()>128 || page<1 || pageSize<1 || pageSize>100) throw new IllegalArgumentException("搜索内容最多 128 字，页码须为正数，每页最多 100 条。");
        Long org=warehouse.getOrganizationId();
        long total=inventory.warehouseStockCount(id,org,term,includeEmpty);
        long current=Math.min((long)page,Math.max(1L,(total+pageSize-1)/pageSize));
        Map<String,Object> result=payloads.map(); result.put("warehouse",warehouse);
        result.put("summary",inventory.warehouseSummary(id,org)); result.put("units",inventory.warehouseUnits(id,org));
        result.put("rows",inventory.warehouseStock(id,org,term,includeEmpty,(current-1)*pageSize,pageSize));
        result.put("total",total); result.put("page",current); result.put("pageSize",pageSize);
        return result;
    }
    public List<InventorySource> sources(Long cycle) { access.cycle(cycle,false); return inventory.sources(cycle); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id) {
        InventorySource source = requireSource(id); access.cycle(source.getCycleId(),false);
        return Map.of("source",source,"positions",inventory.positions(id),"movements",inventory.movements(id));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public WarehouseEntity createWarehouse(@Valid InventoryRequests.Warehouse request) {
        AccessContext.write(); AccessContext.checkOrganization(request.getOrganizationId());
        production.lockOrganization(request.getOrganizationId());
        if (production.activeOrganization(request.getOrganizationId()) != 1) throw new IllegalArgumentException("所属机构不存在或已停用。");
        WarehouseEntity record = new WarehouseEntity(); record.setOrganizationId(request.getOrganizationId()); record.setWarehouseCode(request.getWarehouseCode());
        record.setWarehouseName(request.getWarehouseName().trim()); record.setLocation(request.getLocation().trim()); record.setNotes(text(request.getNotes())); record.setCreatedBy(AccessContext.require().getId());
        inventory.insertWarehouse(record); audit.record("WAREHOUSE_CREATE","WAREHOUSE",record.getId(),record.getWarehouseCode());
        return inventory.warehouse(record.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public InventoryMovementEntity record(Long id, @Valid InventoryRequests.Movement request) {
        InventorySource source = requireSource(id); access.cycle(source.getCycleId(),true);
        PackingLotEntity packing = inventory.lockPacking(id);
        WarehouseEntity warehouse = inventory.warehouse(request.getWarehouseId());
        if (warehouse == null || !source.getOrganizationId().equals(warehouse.getOrganizationId())) throw new IllegalArgumentException("请选择与包装来源同机构的仓库。");
        Map<String,Object> content = payloads.map(); content.put("packing",id); content.put("warehouse",request.getWarehouseId());
        content.put("actor",AccessContext.require().getId()); content.put("code",request.getMovementCode()); content.put("kind",request.getKind());
        content.put("count",request.getPackageCount()); content.put("at",request.getOccurredAt()); content.put("performer",request.getPerformerName().trim());
        content.put("destination",text(request.getDestination())); content.put("reason",request.getReason().trim()); content.put("reference",text(request.getReference()));
        String hash = payloads.requestHash(content);
        InventoryMovementEntity previous = inventory.byRequest(request.getRequestKey());
        if (previous != null) {
            if (!hash.equals(previous.getRequestHash())) throw new IllegalArgumentException("操作标识已用于其他内容，请刷新核对。");
            return inventory.movement(previous.getId());
        }
        LocalDateTime latest = inventory.latestTime(id,request.getWarehouseId());
        if (request.getOccurredAt().isBefore(packing.getPackedAt()) || request.getOccurredAt().isAfter(LocalDateTime.now()) || (latest != null && request.getOccurredAt().isBefore(latest))) {
            throw new IllegalArgumentException("实际时间须在包装之后且不晚于当前时间，也不能早于该仓库本批最近流水；迟到单据需先核对。");
        }
        if ("IN".equals(request.getKind())) {
            if ((long)inventory.received(id) + request.getPackageCount() > packing.getPackageCount()) throw new IllegalArgumentException("累计入库超过实际包装件数，不能重复入库；退回需另走退货流程。");
        } else {
            if ("OUT".equals(request.getKind()) && text(request.getDestination()).isEmpty()) throw new IllegalArgumentException("出库必须填写实际去向或领用用途。");
            if (request.getPackageCount() > inventory.balance(id,request.getWarehouseId())) throw new IllegalArgumentException("该仓库本批库存不足，不能超额出库或登记损耗。");
        }
        InventoryMovementEntity record = new InventoryMovementEntity(); record.setPackingId(id); record.setWarehouseId(request.getWarehouseId()); record.setMovementCode(request.getMovementCode());
        record.setKind(request.getKind()); record.setPackageCount(request.getPackageCount()); record.setPackageUnit(packing.getPackageUnit());
        record.setWeightKg(packing.getNetWeightKg().multiply(BigDecimal.valueOf(request.getPackageCount()))); record.setOccurredAt(request.getOccurredAt());
        record.setPerformerName(request.getPerformerName().trim()); record.setDestination(text(request.getDestination())); record.setReason(request.getReason().trim()); record.setReference(text(request.getReference()));
        record.setRecordedBy(AccessContext.require().getId()); record.setRecorderName(AccessContext.require().getDisplayName()); record.setRequestKey(request.getRequestKey()); record.setRequestHash(hash);
        inventory.insertMovement(record); audit.record("INVENTORY_" + request.getKind(),"INVENTORY_MOVEMENT",record.getId(),record.getMovementCode());
        return inventory.movement(record.getId());
    }
    private InventorySource requireSource(Long id) { InventorySource source=inventory.source(id); if (source==null) throw new IllegalArgumentException("包装来源不存在。"); return source; }
    private String text(String value) { return value == null ? "" : value.trim(); }
}
