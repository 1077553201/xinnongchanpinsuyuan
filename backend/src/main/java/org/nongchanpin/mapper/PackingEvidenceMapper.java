package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.PackingEvidenceModels.*;

@Mapper
public interface PackingEvidenceMapper {
    @Select("SELECT * FROM packing_evidence_job WHERE id=#{id}") Job findJob(Long id);
    @Select("SELECT * FROM packing_evidence_job WHERE id=#{id} FOR UPDATE") Job lockJob(Long id);
    @Select("SELECT * FROM packing_evidence WHERE packing_id=#{id}") Snapshot snapshot(Long id);
    @Insert("INSERT INTO packing_evidence(packing_id,manifest_json,manifest_hash,batch_payload,created_by) VALUES(#{packingId},#{manifestJson},#{manifestHash},#{batchPayload},#{createdBy})") int insertSnapshot(Snapshot snapshot);
    @Update("UPDATE packing_evidence SET lease_token=#{token},lease_until=#{until} WHERE packing_id=#{id}") int claim(@Param("id")Long id,@Param("token")String token,@Param("until")LocalDateTime until);
    @Update("UPDATE packing_evidence SET lease_token=NULL,lease_until=NULL WHERE packing_id=#{id} AND lease_token=#{token}") int release(@Param("id")Long id,@Param("token")String token);
    @Select("SELECT * FROM packing_evidence_job WHERE packing_id=#{id} ORDER BY id DESC LIMIT 100") List<Job> jobs(Long id);
    @Select("SELECT * FROM packing_evidence_job WHERE business_type=#{type} AND business_id=#{id}") Job job(@Param("type")String type,@Param("id")Long id);
    @Insert("INSERT INTO packing_evidence_job(packing_id,business_type,business_id,payload_json,payload_hash) VALUES(#{packingId},#{businessType},#{businessId},#{payloadJson},#{payloadHash})") @Options(useGeneratedKeys=true,keyProperty="id") int insertJob(Job job);
    @Update("UPDATE packing_evidence_job SET chain_status=#{chainStatus},tx_id=#{txId},error_code=#{errorCode},updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}") int updateJob(Job job);
    @Select("SELECT packing_id FROM packing_trace_item WHERE item_id=#{id}") Long packingForItem(Long id);
    @Update("UPDATE product_batch SET batch_metadata=#{metadata} WHERE id=#{id} AND chain_status='NOT_SUBMITTED'") int batchMetadata(@Param("id")Long id,@Param("metadata")String metadata);
    @Select("SELECT id,metric,min_value,max_value,unit,expected_interval_minutes,effective_from,effective_to FROM growing_environment_rule WHERE cycle_id=#{cycle} AND effective_from<=#{until} AND (effective_to IS NULL OR effective_to>#{from}) ORDER BY effective_from,id LIMIT 201") List<Map<String,Object>> rules(@Param("cycle")Long cycle,@Param("from")LocalDateTime from,@Param("until")LocalDateTime until);
    @Select("SELECT t.task_type,e.performed_at,e.input_name,e.quantity,e.unit FROM farm_task t JOIN farm_task_execution e ON e.task_id=t.id WHERE t.cycle_id=#{cycle} AND e.performed_at>=#{from} AND e.performed_at<=#{until} ORDER BY e.performed_at,t.id LIMIT 201") List<Map<String,Object>> farming(@Param("cycle")Long cycle,@Param("from")LocalDateTime from,@Param("until")LocalDateTime until);
    @Select("SELECT w.id,w.metric,w.window_start,w.window_end,w.min_value,w.max_value,w.avg_value,w.sample_count,w.data_hash FROM iot_data_window w JOIN window_provenance p ON p.window_id=w.id WHERE p.cycle_id=#{cycle} AND w.window_start>=#{from} AND w.window_end<=#{until} AND NOT EXISTS (SELECT 1 FROM iot_data_window n JOIN window_provenance np ON np.window_id=n.id WHERE np.cycle_id=p.cycle_id AND np.binding_id=p.binding_id AND n.device_code=w.device_code AND n.metric=w.metric AND n.window_start=w.window_start AND n.window_end=w.window_end AND n.id>w.id) ORDER BY w.window_end DESC,w.id DESC LIMIT 51") List<Map<String,Object>> environment(@Param("cycle")Long cycle,@Param("from")LocalDateTime from,@Param("until")LocalDateTime until);
}
