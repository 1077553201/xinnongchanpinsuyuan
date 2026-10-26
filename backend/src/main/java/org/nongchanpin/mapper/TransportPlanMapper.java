package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.TransportModels.Shipment;
import org.nongchanpin.model.entity.TransportPlanAction;

@Mapper
public interface TransportPlanMapper {
    @Select("SELECT s.*,p.package_count,p.package_unit,p.correction_recorded_at FROM transport_shipment s JOIN transport_plan_origin p ON p.shipment_id=s.id WHERE s.id=#{id}") Shipment original(Long id);
    @Insert("INSERT INTO transport_plan_origin(shipment_id,package_count,package_unit,correction_recorded_at) VALUES(#{id},#{packageCount},#{packageUnit},#{correctionRecordedAt})") int origin(Shipment row);
    @Insert("INSERT INTO transport_code_registry(shipment_code,shipment_id) VALUES(#{code},#{id})") int claimCode(@Param("code")String code,@Param("id")Long id);
    @Select("SELECT COUNT(*) FROM transport_code_registry WHERE shipment_code=#{code}") long codeCount(String code);
    @Select("SELECT COUNT(*) FROM transport_event WHERE shipment_id=#{id}") long eventCount(Long id);
    @Select("SELECT COUNT(*) FROM item_dispatch_link WHERE dispatch_id=#{id}") long linkedCount(Long id);
    @Select("SELECT COUNT(*) FROM item_circulation_evidence e JOIN item_dispatch_link l ON l.item_id=e.item_id WHERE l.dispatch_id=#{id}") long frozenCount(Long id);
    @Select("SELECT * FROM transport_plan_action WHERE shipment_id=#{id} ORDER BY revision LIMIT 101") List<TransportPlanAction> history(Long id);
    @Select("SELECT * FROM transport_plan_action WHERE request_key=#{key}") TransportPlanAction byKey(String key);
    @Insert("INSERT INTO transport_plan_action(shipment_id,revision,action,reason,shipment_code,carrier,tracking_reference,driver_name,driver_contact,notes,package_count,package_unit,correction_recorded_at,impact_json,impact_hash,previous_hash,action_hash,recorded_by,recorded_at,request_key,request_hash) VALUES(#{shipmentId},#{revision},#{action},#{reason},#{shipmentCode},#{carrier},#{trackingReference},#{driverName},#{driverContact},#{notes},#{packageCount},#{packageUnit},#{correctionRecordedAt},#{impactJson},#{impactHash},#{previousHash},#{actionHash},#{recordedBy},#{recordedAt},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(TransportPlanAction row);
}
