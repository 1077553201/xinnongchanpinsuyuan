package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.*;

@Mapper
public interface SalesMapper {
    String FILTER=" WHERE (#{org} IS NULL OR organization_id=#{org}) AND (#{status}='' OR status=#{status}) AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',order_code,customer_name,delivery_address))>0) ";
    @Select("SELECT COUNT(*) FROM sales_order"+FILTER) long count(@Param("org") Long org,@Param("search") String search,@Param("status") String status);
    @Select("SELECT * FROM sales_order"+FILTER+" ORDER BY (status IN ('OPEN','PARTIAL')) DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<SalesOrderEntity> orders(@Param("org") Long org,@Param("search") String search,@Param("status") String status,@Param("offset") long offset,@Param("size") int size);
    @Select("SELECT * FROM sales_order WHERE id=#{id}") SalesOrderEntity order(Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM sales_order WHERE id=#{id} FOR UPDATE") SalesOrderEntity lockOrder(Long id);
    @Select("SELECT * FROM sales_order WHERE request_key=#{key}") SalesOrderEntity orderByRequest(String key);
    String LINE_VIEW="SELECT l.*,h.cycle_id,COALESCE((SELECT SUM(m.package_count) FROM sales_dispatch d JOIN inventory_movement m ON m.id=d.movement_id WHERE d.line_id=l.id),0) AS dispatched_count FROM sales_order_line l JOIN packing_lot p ON p.id=l.packing_id JOIN harvest_grade g ON g.id=p.grade_id JOIN harvest_lot h ON h.id=g.harvest_id ";
    @Select(LINE_VIEW+"WHERE l.order_id=#{id} ORDER BY l.id") List<SalesLineEntity> lines(Long id);
    @Select(LINE_VIEW+"WHERE l.id=#{id}") SalesLineEntity line(Long id);
    @Select("SELECT order_id FROM sales_order_line WHERE id=#{id}") Long lineOrderId(Long id);
    String DISPATCH_VIEW="SELECT d.*,(SELECT t.id FROM transport_shipment t WHERE t.dispatch_id=d.id) AS shipment_id,m.movement_code,m.package_count,m.package_unit,m.weight_kg,m.occurred_at,m.performer_name,m.recorder_name,m.recorded_at,m.reason,m.reference FROM sales_dispatch d JOIN inventory_movement m ON m.id=d.movement_id ";
    @Select(DISPATCH_VIEW+"JOIN sales_order_line l ON l.id=d.line_id WHERE l.order_id=#{id} ORDER BY d.id DESC LIMIT 200") List<SalesDispatchEntity> dispatches(Long id);
    @Select(DISPATCH_VIEW+"WHERE d.id=#{id}") SalesDispatchEntity dispatch(Long id);
    @Select("SELECT * FROM sales_dispatch WHERE request_key=#{key}") SalesDispatchEntity dispatchByRequest(String key);
    @Insert("INSERT INTO sales_order (organization_id,order_code,ordered_on,customer_name,customer_contact,delivery_address,notes,total_amount,recorded_by,recorder_name,request_key,request_hash) VALUES (#{organizationId},#{orderCode},#{orderedOn},#{customerName},#{customerContact},#{deliveryAddress},#{notes},#{totalAmount},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertOrder(SalesOrderEntity record);
    @Insert("INSERT INTO sales_order_line (order_id,packing_id,warehouse_id,packing_code,warehouse_name,product_name,variety,package_unit,net_weight_kg,package_count,unit_price,line_amount) VALUES (#{orderId},#{packingId},#{warehouseId},#{packingCode},#{warehouseName},#{productName},#{variety},#{packageUnit},#{netWeightKg},#{packageCount},#{unitPrice},#{lineAmount})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertLine(SalesLineEntity record);
    @Insert("INSERT INTO sales_dispatch (line_id,movement_id,request_key,request_hash) VALUES (#{lineId},#{movementId},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertDispatch(SalesDispatchEntity record);
    @Update("UPDATE sales_order SET status=#{status} WHERE id=#{id}") int status(@Param("id") Long id,@Param("status") String status);
    @Update("UPDATE sales_order SET status=#{status},closed_by=#{closedBy},closed_at=#{closedAt},close_reason=#{closeReason} WHERE id=#{id}") int close(SalesOrderEntity record);
}
