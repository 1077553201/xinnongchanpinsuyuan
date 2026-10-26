package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.model.entity.TraceItemEntity;

@Mapper
public interface PackingTraceMapper {
    @Select("SELECT p.*,g.grade_name,h.id AS harvest_id,h.lot_code AS harvest_code,h.harvested_at,c.id AS cycle_id,c.product_id,c.product_name,c.variety,c.cycle_code,c.planted_on,f.id AS plot_id,f.plot_code,f.plot_name,f.organization_id FROM packing_lot p JOIN harvest_grade g ON g.id=p.grade_id JOIN harvest_lot h ON h.id=g.harvest_id JOIN planting_cycle c ON c.id=h.cycle_id JOIN farm_plot f ON f.id=c.plot_id WHERE p.id=#{id}") Source source(Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    @Select("SELECT id FROM packing_lot WHERE id=#{id} FOR UPDATE") Long lock(Long id);
    @Select("SELECT * FROM packing_trace_batch WHERE packing_id=#{id}") Bridge bridge(Long id);
    @Select("SELECT b.* FROM packing_trace_batch b JOIN packing_trace_item i ON i.packing_id=b.packing_id WHERE i.item_id=#{id}") Bridge publicBridge(Long id);
    @Insert("INSERT INTO packing_trace_batch(packing_id,batch_id,source_json,source_hash) VALUES(#{packingId},#{batchId},#{sourceJson},#{sourceHash})") int insertBridge(Bridge bridge);
    @Select("SELECT COUNT(*) FROM packing_trace_item WHERE packing_id=#{id}") long count(Long id);
    @Select("SELECT id,packing_id,code_count AS count,request_key,request_hash,recorded_by FROM packing_trace_issue WHERE request_key=#{key}") Issue byKey(String key);
    @Insert("INSERT INTO packing_trace_issue(packing_id,code_count,request_key,request_hash,recorded_by) VALUES(#{packingId},#{count},#{requestKey},#{requestHash},#{recordedBy})") @Options(useGeneratedKeys=true,keyProperty="id") int insertIssue(Issue issue);
    @Insert("INSERT INTO packing_trace_item(item_id,packing_id,issue_id,ordinal) VALUES(#{item},#{packing},#{issue},#{ordinal})") int link(@Param("item")Long item,@Param("packing")Long packing,@Param("issue")Long issue,@Param("ordinal")long ordinal);
    @Select("SELECT t.* FROM trace_item t JOIN packing_trace_item i ON i.item_id=t.id WHERE i.packing_id=#{id} ORDER BY i.ordinal DESC LIMIT 20 OFFSET #{offset}") List<TraceItemEntity> items(@Param("id")Long id,@Param("offset")long offset);
    @Select("SELECT t.* FROM trace_item t JOIN packing_trace_item i ON i.item_id=t.id WHERE i.issue_id=#{id} ORDER BY i.ordinal") List<TraceItemEntity> issueItems(Long id);
    @Select("SELECT id,packing_id,code_count AS count,request_key,request_hash,recorded_by FROM packing_trace_issue WHERE id=#{id}") Issue issue(Long id);
}
