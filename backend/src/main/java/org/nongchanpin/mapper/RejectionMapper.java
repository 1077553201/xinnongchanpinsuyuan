package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.RejectionModels.*;
import org.nongchanpin.model.entity.RejectionModels.Case;

@Mapper
public interface RejectionMapper {
    String SOURCE="SELECT o.organization_id,o.id order_id,d.id dispatch_id,p.packing_id,t.id item_id,t.trace_code_plain trace_code,t.trace_code_hash,l.receipt_outcome,l.receipt_at receipt_recorded_at,e.id receipt_event_id,e.occurred_at receipt_occurred_at,b.source_hash,s.packing_code,s.product_name,s.variety,s.package_unit,s.net_weight_kg FROM item_dispatch_link l JOIN trace_item t ON t.id=l.item_id JOIN packing_trace_item p ON p.item_id=l.item_id JOIN packing_trace_batch b ON b.packing_id=p.packing_id JOIN sales_dispatch d ON d.id=l.dispatch_id JOIN sales_order_line s ON s.id=d.line_id AND s.packing_id=p.packing_id JOIN sales_order o ON o.id=s.order_id JOIN transport_shipment h ON h.dispatch_id=d.id JOIN transport_event e ON e.id=l.receipt_event_id AND e.shipment_id=h.id AND e.kind='RECEIVE' ";
    @Select(SOURCE+" WHERE l.item_id=#{item} AND l.dispatch_id=#{dispatch}") Source source(@Param("dispatch")Long dispatch,@Param("item")Long item);
    String VIEW=" FROM rejection_case c JOIN trace_item t ON t.id=c.item_id JOIN sales_dispatch d ON d.id=c.dispatch_id JOIN sales_order_line l ON l.id=d.line_id LEFT JOIN inventory_warehouse w ON w.id=c.warehouse_id ";
    String COLUMNS="c.*,t.trace_code_plain trace_code,l.packing_code,l.product_name,l.variety,w.warehouse_name";
    String FILTER=" WHERE (#{org} IS NULL OR c.organization_id=#{org}) AND (#{warehouse} IS NULL OR c.warehouse_id=#{warehouse}) AND (#{status}='ALL' OR c.status=#{status}) AND (#{code}='' OR t.trace_code_plain=#{code}) ";
    @Select("SELECT "+COLUMNS+VIEW+FILTER+" ORDER BY (c.status='DISPOSED'),c.id DESC LIMIT 20 OFFSET #{offset}")
    List<Case> list(@Param("org")Long org,@Param("warehouse")Long warehouse,@Param("status")String status,@Param("code")String code,@Param("offset")long offset);
    @Select("SELECT COUNT(*)"+VIEW+FILTER) long count(@Param("org")Long org,@Param("warehouse")Long warehouse,@Param("status")String status,@Param("code")String code);
    @Select("SELECT package_unit,COUNT(*) package_count,SUM(net_weight_kg) weight_kg FROM rejection_case WHERE status='QUARANTINED' AND (#{org} IS NULL OR organization_id=#{org}) AND (#{warehouse} IS NULL OR warehouse_id=#{warehouse}) GROUP BY package_unit ORDER BY package_unit")
    List<UnitBalance> quarantineUnits(@Param("org")Long org,@Param("warehouse")Long warehouse);
    @Select("SELECT "+COLUMNS+VIEW+" WHERE c.id=#{id}") Case find(Long id);
    @Select("SELECT * FROM rejection_case WHERE id=#{id} FOR UPDATE") Case lock(Long id);
    @Select("SELECT * FROM rejection_case WHERE item_id=#{id}") Case byItem(Long id);
    @Select("SELECT * FROM rejection_case WHERE request_key=#{key}") Case byKey(String key);
    @Insert("INSERT INTO rejection_case(organization_id,dispatch_id,packing_id,item_id,source_json,source_hash,initial_hash,state_hash,status,revision,package_unit,net_weight_kg,last_physical_at,reason,recorded_by,request_key,request_hash) VALUES(#{organizationId},#{dispatchId},#{packingId},#{itemId},#{sourceJson},#{sourceHash},#{initialHash},#{stateHash},#{status},#{revision},#{packageUnit},#{netWeightKg},#{lastPhysicalAt},#{reason},#{recordedBy},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(Case row);
    @Update("UPDATE rejection_case SET status=#{status},revision=#{revision},state_hash=#{stateHash},warehouse_id=#{warehouseId},quarantine_location=#{quarantineLocation},last_physical_at=#{lastPhysicalAt} WHERE id=#{id}") int state(Case row);
    @Select("SELECT e.*,w.warehouse_name FROM rejection_event e LEFT JOIN inventory_warehouse w ON w.id=e.warehouse_id WHERE case_id=#{id} ORDER BY revision LIMIT 101") List<Event> events(Long id);
    @Select("SELECT * FROM rejection_event WHERE request_key=#{key}") Event eventByKey(String key);
    @Insert("INSERT INTO rejection_event(case_id,revision,kind,occurred_at,warehouse_id,quarantine_location,performer_name,note,reference,target_revision,previous_hash,event_hash,payload_json,recorded_by,recorded_at,request_key,request_hash) VALUES(#{caseId},#{revision},#{kind},#{occurredAt},#{warehouseId},#{quarantineLocation},#{performerName},#{note},#{reference},#{targetRevision},#{previousHash},#{eventHash},#{payloadJson},#{recordedBy},#{recordedAt},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertEvent(Event row);
}
