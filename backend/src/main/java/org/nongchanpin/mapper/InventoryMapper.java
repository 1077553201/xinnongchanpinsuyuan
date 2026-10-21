package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.*;

@Mapper
public interface InventoryMapper {
    String SOURCE_VIEW = "SELECT p.*,g.harvest_id,g.grade_name,h.lot_code AS harvest_code,c.id AS cycle_id,c.cycle_code,c.product_name,c.variety,f.plot_name,f.organization_id, "
            + "COALESCE((SELECT SUM(m.package_count) FROM inventory_movement m WHERE m.packing_id=p.id AND m.kind='IN'),0) AS received_count, "
            + "COALESCE((SELECT SUM(m.package_count) FROM inventory_movement m WHERE m.packing_id=p.id AND m.kind='OUT'),0) AS dispatched_count, "
            + "COALESCE((SELECT SUM(m.package_count) FROM inventory_movement m WHERE m.packing_id=p.id AND m.kind='LOSS'),0) AS loss_count, "
            + "COALESCE((SELECT SUM(CASE WHEN m.kind='IN' THEN m.package_count ELSE -m.package_count END) FROM inventory_movement m WHERE m.packing_id=p.id),0) AS available_count "
            + "FROM packing_lot p JOIN harvest_grade g ON g.id=p.grade_id JOIN harvest_lot h ON h.id=g.harvest_id JOIN planting_cycle c ON c.id=h.cycle_id JOIN farm_plot f ON f.id=c.plot_id ";
    @Select(SOURCE_VIEW + "WHERE c.id=#{cycle} ORDER BY (received_count < p.package_count) DESC,p.id DESC LIMIT 200")
    List<InventorySource> sources(Long cycle);
    @Select(SOURCE_VIEW + "WHERE p.id=#{id}") InventorySource source(Long id);
    @Select("SELECT * FROM packing_lot WHERE id=#{id} FOR UPDATE") PackingLotEntity lockPacking(Long id);
    @Select("SELECT * FROM inventory_warehouse WHERE (#{org} IS NULL OR organization_id=#{org}) ORDER BY id DESC")
    List<WarehouseEntity> warehouses(@Param("org") Long org);
    @Select("SELECT * FROM inventory_warehouse WHERE id=#{id}") WarehouseEntity warehouse(Long id);
    @Insert("INSERT INTO inventory_warehouse (organization_id,warehouse_code,warehouse_name,location,notes,created_by) "
            + "VALUES (#{organizationId},#{warehouseCode},#{warehouseName},#{location},#{notes},#{createdBy})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertWarehouse(WarehouseEntity record);
    @Select("SELECT COALESCE(SUM(package_count),0) FROM inventory_movement WHERE packing_id=#{id} AND kind='IN'") int received(Long id);
    @Select("SELECT COALESCE(SUM(CASE WHEN kind='IN' THEN package_count ELSE -package_count END),0) FROM inventory_movement WHERE packing_id=#{packing} AND warehouse_id=#{warehouse}")
    int balance(@Param("packing") Long packing, @Param("warehouse") Long warehouse);
    @Select("SELECT MAX(occurred_at) FROM inventory_movement WHERE packing_id=#{packing} AND warehouse_id=#{warehouse}")
    LocalDateTime latestTime(@Param("packing") Long packing, @Param("warehouse") Long warehouse);
    @Select("SELECT w.id AS warehouse_id,w.warehouse_code,w.warehouse_name,w.location, "
            + "SUM(CASE WHEN m.kind='IN' THEN m.package_count ELSE 0 END) AS received_count, "
            + "SUM(CASE WHEN m.kind='OUT' THEN m.package_count ELSE 0 END) AS dispatched_count, "
            + "SUM(CASE WHEN m.kind='LOSS' THEN m.package_count ELSE 0 END) AS loss_count, "
            + "SUM(CASE WHEN m.kind='IN' THEN m.package_count ELSE -m.package_count END) AS available_count "
            + "FROM inventory_movement m JOIN inventory_warehouse w ON w.id=m.warehouse_id WHERE m.packing_id=#{id} GROUP BY w.id,w.warehouse_code,w.warehouse_name,w.location ORDER BY available_count DESC,w.id")
    List<InventoryPosition> positions(Long id);
    @Select("SELECT m.*,w.warehouse_name FROM inventory_movement m JOIN inventory_warehouse w ON w.id=m.warehouse_id WHERE m.packing_id=#{id} ORDER BY m.occurred_at DESC,m.id DESC LIMIT 200")
    List<InventoryMovementEntity> movements(Long id);
    @Select("SELECT * FROM inventory_movement WHERE request_key=#{key}") InventoryMovementEntity byRequest(String key);
    @Select("SELECT m.*,w.warehouse_name FROM inventory_movement m JOIN inventory_warehouse w ON w.id=m.warehouse_id WHERE m.id=#{id}") InventoryMovementEntity movement(Long id);
    @Insert("INSERT INTO inventory_movement (packing_id,warehouse_id,movement_code,kind,package_count,package_unit,weight_kg,occurred_at,performer_name,destination,reason,reference,recorded_by,recorder_name,request_key,request_hash) "
            + "VALUES (#{packingId},#{warehouseId},#{movementCode},#{kind},#{packageCount},#{packageUnit},#{weightKg},#{occurredAt},#{performerName},#{destination},#{reason},#{reference},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertMovement(InventoryMovementEntity record);
}
