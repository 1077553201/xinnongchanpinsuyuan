package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.*;

@Mapper
public interface DispatchCorrectionMapper {
    @Select("SELECT * FROM sales_dispatch WHERE movement_id=#{id}") SalesDispatchEntity dispatch(Long id);
    @Select("SELECT * FROM sales_dispatch_correction WHERE movement_id=#{id} ORDER BY revision LIMIT 101") List<LossCorrectionEntity> history(Long id);
    @Select("SELECT * FROM sales_dispatch_correction WHERE request_key=#{key}") LossCorrectionEntity byKey(String key);
    @Insert("INSERT INTO sales_dispatch_correction(movement_id,revision,previous_count,corrected_count,balance_before,balance_after,verified_at,performer_name,reason,reference,original_hash,previous_hash,impact_json,impact_hash,payload_json,correction_hash,recorded_by,recorded_at,request_key,request_hash) VALUES(#{movementId},#{revision},#{previousCount},#{correctedCount},#{balanceBefore},#{balanceAfter},#{verifiedAt},#{performerName},#{reason},#{reference},#{originalHash},#{previousHash},#{impactJson},#{impactHash},#{payloadJson},#{correctionHash},#{recordedBy},#{recordedAt},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(LossCorrectionEntity row);
    @Select("SELECT MAX(id) FROM inventory_movement WHERE packing_id=#{packing} AND warehouse_id=#{warehouse}") Long latestMovement(@Param("packing")Long packing,@Param("warehouse")Long warehouse);
    @Select("SELECT COUNT(*) FROM transport_shipment WHERE dispatch_id=#{id} AND status<>'CANCELLED'") long shipmentCount(Long id);
    @Select("SELECT COUNT(*) FROM item_dispatch_link WHERE dispatch_id=#{id}") long linkedCount(Long id);
    @Select("SELECT COUNT(*) FROM item_circulation_evidence e JOIN item_dispatch_link l ON l.item_id=e.item_id WHERE l.dispatch_id=#{id}") long frozenCount(Long id);
}
