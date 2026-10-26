package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.TransportModels.*;

@Mapper
public interface TransportMapper {
    @Select("SELECT s.id,CONCAT_WS(' · ',s.shipment_code,l.product_name,s.status,CONCAT(m.effective_count,m.package_unit)) AS label FROM transport_current_plan s JOIN sales_dispatch d ON d.id=s.dispatch_id JOIN sales_order_line l ON l.id=d.line_id JOIN sales_order o ON o.id=l.order_id JOIN inventory_effective_movement m ON m.id=d.movement_id WHERE s.status IN ('PLANNED','IN_TRANSIT') AND (#{org} IS NULL OR o.organization_id=#{org}) AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',s.shipment_code,l.product_name))>0) ORDER BY s.id DESC LIMIT 50")
    List<java.util.Map<String,Object>> agentShipments(@Param("org")Long org,@Param("search")String search);
    String VIEW="SELECT s.*,o.organization_id,o.id AS order_id,o.order_code,l.packing_id,l.warehouse_id,l.product_name,l.variety,l.packing_code,l.warehouse_name,m.movement_code,m.occurred_at AS dispatched_at FROM transport_current_plan s JOIN sales_dispatch d ON d.id=s.dispatch_id JOIN sales_order_line l ON l.id=d.line_id JOIN sales_order o ON o.id=l.order_id JOIN inventory_effective_movement m ON m.id=d.movement_id ";
    String FILTER=" WHERE (#{org} IS NULL OR o.organization_id=#{org}) AND (#{status}='' OR s.status=#{status}) AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',s.shipment_code,o.order_code,s.recipient,s.carrier,s.tracking_reference))>0 OR EXISTS (SELECT 1 FROM transport_code_registry r WHERE r.shipment_id=s.id AND LOCATE(#{search},r.shipment_code)>0)) ";
    @Select("SELECT COUNT(*) FROM transport_current_plan s JOIN sales_dispatch d ON d.id=s.dispatch_id JOIN sales_order_line l ON l.id=d.line_id JOIN sales_order o ON o.id=l.order_id"+FILTER)
    long count(@Param("org") Long org,@Param("search") String search,@Param("status") String status);
    @Select(VIEW+FILTER+" ORDER BY s.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Shipment> list(@Param("org") Long org,@Param("search") String search,@Param("status") String status,@Param("offset") long offset,@Param("size") int size);
    @Select(VIEW+"WHERE s.id=#{id}") Shipment shipment(Long id);
    @Select("SELECT o.organization_id FROM transport_current_plan s JOIN sales_dispatch d ON d.id=s.dispatch_id JOIN sales_order_line l ON l.id=d.line_id JOIN sales_order o ON o.id=l.order_id WHERE s.id=#{id}") Long organization(Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    @Select("SELECT id FROM transport_shipment WHERE id=#{id} FOR UPDATE") Long lock(Long id);
    @Select("SELECT * FROM transport_shipment WHERE request_key=#{key}") Shipment byRequest(String key);
    @Select("SELECT * FROM transport_shipment WHERE dispatch_id=#{id}") Shipment byDispatch(Long id);
    @Select("SELECT * FROM transport_event WHERE shipment_id=#{id} ORDER BY id DESC LIMIT 200") List<Event> events(Long id);
    @Select("SELECT * FROM transport_event WHERE shipment_id=#{id} ORDER BY id DESC LIMIT 1") Event latest(Long id);
    @Select("SELECT * FROM transport_event WHERE shipment_id=#{id} AND kind='RECEIVE' ORDER BY id DESC LIMIT 1") Event receipt(Long id);
    @Select("SELECT kind,occurred_at FROM transport_event WHERE shipment_id=#{id} ORDER BY occurred_at DESC,id DESC LIMIT 201") List<Event> publicEvents(Long id);
    @Select("SELECT * FROM transport_event WHERE request_key=#{key}") Event eventByRequest(String key);
    @Insert("INSERT INTO transport_shipment (dispatch_id,shipment_code,carrier,tracking_reference,driver_name,driver_contact,recipient,delivery_address,notes,recorded_by,recorder_name,request_key,request_hash) VALUES (#{dispatchId},#{shipmentCode},#{carrier},#{trackingReference},#{driverName},#{driverContact},#{recipient},#{deliveryAddress},#{notes},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(Shipment shipment);
    @Insert("INSERT INTO transport_event (shipment_id,kind,occurred_at,location,performer_name,description,reference,accepted_count,rejected_count,recorded_by,recorder_name,request_key,request_hash) VALUES (#{shipmentId},#{kind},#{occurredAt},#{location},#{performerName},#{description},#{reference},#{acceptedCount},#{rejectedCount},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertEvent(Event event);
    @Update("UPDATE transport_shipment SET status=#{status} WHERE id=#{id}") int status(@Param("id")Long id,@Param("status")String status);
}
