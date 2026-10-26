package org.nongchanpin.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.LossCorrectionEntity;

@Mapper
public interface LossCorrectionMapper {
    @Select("SELECT * FROM inventory_loss_correction WHERE movement_id=#{id} ORDER BY revision LIMIT 101") List<LossCorrectionEntity> history(Long id);
    @Select("SELECT * FROM inventory_loss_correction WHERE request_key=#{key}") LossCorrectionEntity byKey(String key);
    @Insert("INSERT INTO inventory_loss_correction(movement_id,revision,previous_count,corrected_count,balance_before,balance_after,verified_at,performer_name,reason,reference,original_hash,previous_hash,impact_json,impact_hash,payload_json,correction_hash,recorded_by,recorded_at,request_key,request_hash) VALUES(#{movementId},#{revision},#{previousCount},#{correctedCount},#{balanceBefore},#{balanceAfter},#{verifiedAt},#{performerName},#{reason},#{reference},#{originalHash},#{previousHash},#{impactJson},#{impactHash},#{payloadJson},#{correctionHash},#{recordedBy},#{recordedAt},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(LossCorrectionEntity row);
    @Select("SELECT l.id AS lineId,o.id AS orderId,o.order_code AS orderCode,o.status,l.warehouse_id AS warehouseId,l.package_count AS orderedCount,COALESCE((SELECT SUM(m.package_count) FROM sales_dispatch d JOIN inventory_movement m ON m.id=d.movement_id WHERE d.line_id=l.id),0) AS dispatchedCount FROM sales_order_line l JOIN sales_order o ON o.id=l.order_id WHERE l.packing_id=#{id} ORDER BY l.id LIMIT 201") List<Map<String,Object>> orders(Long id);
    @Select("SELECT i.item_id AS itemId,t.trace_code_plain AS traceCode,l.dispatch_id AS dispatchId,l.receipt_outcome AS receiptOutcome,r.status AS rejectionStatus FROM packing_trace_item i JOIN trace_item t ON t.id=i.item_id LEFT JOIN item_dispatch_link l ON l.item_id=i.item_id LEFT JOIN rejection_case r ON r.item_id=i.item_id WHERE i.packing_id=#{id} ORDER BY i.item_id LIMIT 201") List<Map<String,Object>> codes(Long id);
    @Select("SELECT 'PRODUCTION' AS kind,j.id,j.business_id AS itemId,1 AS version,j.chain_status AS chainStatus FROM packing_evidence_job j WHERE j.packing_id=#{id} UNION ALL SELECT 'CIRCULATION',e.id,e.item_id,e.version,e.chain_status FROM item_circulation_evidence e JOIN packing_trace_item i ON i.item_id=e.item_id WHERE i.packing_id=#{id} UNION ALL SELECT 'QUALITY',e.id,e.item_id,e.version,e.chain_status FROM quality_item_evidence e JOIN packing_trace_item i ON i.item_id=e.item_id WHERE i.packing_id=#{id} ORDER BY kind,id LIMIT 201") List<Map<String,Object>> evidence(Long id);
}
