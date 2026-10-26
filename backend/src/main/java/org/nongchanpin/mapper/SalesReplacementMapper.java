package org.nongchanpin.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.SalesReplacementEntity;

@Mapper
public interface SalesReplacementMapper {
    @Select("SELECT COUNT(*) FROM sales_dispatch d JOIN sales_order_line l ON l.id=d.line_id WHERE l.order_id=#{id}") long dispatchCount(Long id);
    @Select("SELECT d.id AS dispatchId,m.effective_count AS packageCount,s.id AS shipmentId,s.status AS shipmentStatus,(SELECT COUNT(*) FROM item_dispatch_link i WHERE i.dispatch_id=d.id) AS linkedCodeCount,(SELECT COUNT(*) FROM item_dispatch_link i WHERE i.dispatch_id=d.id AND i.receipt_outcome IS NOT NULL) AS checkedCodeCount FROM sales_dispatch d JOIN sales_order_line l ON l.id=d.line_id JOIN inventory_effective_movement m ON m.id=d.movement_id LEFT JOIN transport_shipment s ON s.dispatch_id=d.id WHERE l.order_id=#{id} ORDER BY d.id LIMIT 201") List<Map<String,Object>> dependencies(Long id);
    @Select("SELECT * FROM sales_order_replacement WHERE original_order_id=#{id} OR replacement_order_id=#{id} ORDER BY id") List<SalesReplacementEntity> relations(Long id);
    @Select("SELECT * FROM sales_order_replacement WHERE original_order_id=#{id}") SalesReplacementEntity outgoing(Long id);
    @Select("SELECT * FROM sales_order_replacement WHERE request_key=#{key}") SalesReplacementEntity byKey(String key);
    @Select("SELECT COUNT(*) FROM sales_order WHERE order_code=#{code}") long codeCount(String code);
    @Insert("INSERT INTO sales_order_replacement(organization_id,original_order_id,replacement_order_id,reason,impact_json,impact_hash,record_hash,recorded_by,recorded_at,request_key,request_hash) VALUES(#{organizationId},#{originalOrderId},#{replacementOrderId},#{reason},#{impactJson},#{impactHash},#{recordHash},#{recordedBy},#{recordedAt},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(SalesReplacementEntity row);
}
