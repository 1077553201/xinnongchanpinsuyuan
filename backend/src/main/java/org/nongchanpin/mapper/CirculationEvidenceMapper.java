package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.CirculationEvidenceEntity;

@Mapper
public interface CirculationEvidenceMapper {
    @Select("SELECT id FROM sales_order WHERE id=(SELECT l.order_id FROM item_dispatch_link i JOIN sales_dispatch d ON d.id=i.dispatch_id JOIN sales_order_line l ON l.id=d.line_id WHERE i.item_id=#{id}) FOR UPDATE") Long lockOrderForItem(Long id);
    @Select("SELECT id FROM transport_shipment WHERE dispatch_id=(SELECT dispatch_id FROM item_dispatch_link WHERE item_id=#{id})") Long shipmentForItem(Long id);
    @Select("SELECT id FROM transport_shipment WHERE id=#{id} FOR UPDATE") Long lockShipment(Long id);
    @Select("SELECT * FROM item_circulation_evidence WHERE item_id=#{id} ORDER BY version DESC LIMIT 1") CirculationEvidenceEntity latest(Long id);
    @Select("SELECT * FROM item_circulation_evidence WHERE id=#{id}") CirculationEvidenceEntity find(Long id);
    @Select("SELECT * FROM item_circulation_evidence WHERE item_id=#{item} AND version=#{version}") CirculationEvidenceEntity byVersion(@Param("item")Long item,@Param("version")int version);
    @Select("SELECT * FROM item_circulation_evidence WHERE item_id=#{id} ORDER BY version DESC LIMIT 50") List<CirculationEvidenceEntity> versions(Long id);
    @Insert("INSERT INTO item_circulation_evidence(item_id,version,manifest_json,manifest_hash,previous_hash,event_id,created_by) VALUES(#{itemId},#{version},#{manifestJson},#{manifestHash},#{previousHash},#{eventId},#{createdBy})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(CirculationEvidenceEntity record);
    @Update("UPDATE item_circulation_evidence SET chain_status=#{chainStatus},tx_id=#{txId},error_code=#{errorCode} WHERE id=#{id}") int state(CirculationEvidenceEntity record);
    @Update("UPDATE item_circulation_evidence SET lease_token=#{token},lease_until=#{until} WHERE id=#{id}") int claim(@Param("id")Long id,@Param("token")String token,@Param("until")LocalDateTime until);
    @Update("UPDATE item_circulation_evidence SET lease_token=NULL,lease_until=NULL WHERE id=#{id} AND lease_token=#{token}") int release(@Param("id")Long id,@Param("token")String token);
}
