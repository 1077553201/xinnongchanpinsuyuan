package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.ItemCirculationModels.*;

@Mapper
public interface ItemCirculationMapper {
    String VIEW=" FROM packing_trace_item p JOIN trace_item t ON t.id=p.item_id LEFT JOIN item_dispatch_link l ON l.item_id=p.item_id ";
    String FILTER=" WHERE p.packing_id=#{packing} AND (#{search}='' OR LOCATE(#{search},t.trace_code_plain)>0) AND (#{mode}='ALL' OR (#{mode}='AVAILABLE' AND l.item_id IS NULL) OR (#{mode}='ASSIGNED' AND l.dispatch_id=#{dispatch})) ";
    @Select("SELECT COUNT(*)"+VIEW+FILTER) long count(@Param("packing")Long packing,@Param("dispatch")Long dispatch,@Param("search")String search,@Param("mode")String mode);
    @Select("SELECT p.item_id,t.trace_code_plain AS trace_code,l.dispatch_id,l.receipt_outcome,l.linked_at,l.receipt_at"+VIEW+FILTER+" ORDER BY p.ordinal DESC LIMIT 20 OFFSET #{offset}")
    List<Item> items(@Param("packing")Long packing,@Param("dispatch")Long dispatch,@Param("search")String search,@Param("mode")String mode,@Param("offset")long offset);
    @Select("SELECT p.item_id,t.trace_code_plain AS trace_code,l.dispatch_id,l.receipt_outcome,l.linked_at,l.receipt_at"+VIEW+" WHERE p.packing_id=#{packing} AND p.item_id=#{item}") Item item(@Param("packing")Long packing,@Param("item")Long item);
    @Select("SELECT COUNT(*) FROM item_dispatch_link WHERE dispatch_id=#{id}") long linkedCount(Long id);
    @Select("SELECT COUNT(*) FROM item_dispatch_link WHERE dispatch_id=#{id} AND receipt_outcome=#{outcome}") long receiptCount(@Param("id")Long id,@Param("outcome")String outcome);
    @Select("SELECT * FROM item_dispatch_operation WHERE request_key=#{key}") Operation operation(String key);
    @Insert("INSERT INTO item_dispatch_operation(request_key,dispatch_id,request_hash,recorded_by) VALUES(#{requestKey},#{dispatchId},#{requestHash},#{recordedBy})") int insertOperation(Operation record);
    @Insert("INSERT INTO item_dispatch_link(item_id,dispatch_id,link_request_key,linked_by) VALUES(#{item},#{dispatch},#{key},#{actor})") int link(@Param("item")Long item,@Param("dispatch")Long dispatch,@Param("key")String key,@Param("actor")Long actor);
    @Update("UPDATE item_dispatch_link SET receipt_event_id=#{event},receipt_outcome=#{outcome},receipt_request_key=#{key},receipt_by=#{actor},receipt_at=CURRENT_TIMESTAMP(6) WHERE item_id=#{item} AND dispatch_id=#{dispatch} AND receipt_outcome IS NULL")
    int receive(@Param("item")Long item,@Param("dispatch")Long dispatch,@Param("event")Long event,@Param("outcome")String outcome,@Param("key")String key,@Param("actor")Long actor);
    @Select("SELECT * FROM item_dispatch_link WHERE item_id=#{id}") Item publicLink(Long id);
}
