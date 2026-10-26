package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.LossCorrectionEntity;

@Mapper
public interface MovementCorrectionMapper {
    @Select("SELECT * FROM inventory_quantity_correction WHERE movement_id=#{id} ORDER BY revision LIMIT 101") List<LossCorrectionEntity> history(Long id);
    @Select("SELECT * FROM inventory_quantity_correction WHERE request_key=#{key}") LossCorrectionEntity byKey(String key);
    @Insert("INSERT INTO inventory_quantity_correction(movement_id,revision,previous_count,corrected_count,balance_before,balance_after,verified_at,performer_name,reason,reference,original_hash,previous_hash,impact_json,impact_hash,payload_json,correction_hash,recorded_by,recorded_at,request_key,request_hash) VALUES(#{movementId},#{revision},#{previousCount},#{correctedCount},#{balanceBefore},#{balanceAfter},#{verifiedAt},#{performerName},#{reason},#{reference},#{originalHash},#{previousHash},#{impactJson},#{impactHash},#{payloadJson},#{correctionHash},#{recordedBy},#{recordedAt},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(LossCorrectionEntity row);
    @Select("SELECT MAX(id) FROM inventory_movement WHERE packing_id=#{packing} AND warehouse_id=#{warehouse}") Long latestMovement(@Param("packing")Long packing,@Param("warehouse")Long warehouse);
    @Select("SELECT COUNT(*) FROM sales_dispatch WHERE movement_id=#{id}") long salesDispatchCount(Long id);
    @Select("SELECT COUNT(*) FROM sales_order_line WHERE packing_id=#{id}") long orderLineCount(Long id);
    @Select("SELECT COUNT(*) FROM item_dispatch_link l JOIN packing_trace_item i ON i.item_id=l.item_id WHERE i.packing_id=#{id}") long linkedCodeCount(Long id);
}
