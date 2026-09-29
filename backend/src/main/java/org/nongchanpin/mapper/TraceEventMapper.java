package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.TraceEventEntity;

@Mapper
public interface TraceEventMapper {
    @Insert("INSERT INTO trace_event (item_id, batch_id, event_type, event_time, location, operator_name, canonical_payload_hash, event_hash, payload, chain_status, status) "
            + "VALUES (#{itemId}, #{batchId}, #{eventType}, #{eventTime}, #{location}, #{operatorName}, #{canonicalPayloadHash}, #{eventHash}, #{payload}, #{chainStatus}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TraceEventEntity event);

    @Select("SELECT * FROM trace_event WHERE item_id = #{itemId} AND status = 'ACTIVE' ORDER BY event_time ASC, id ASC")
    List<TraceEventEntity> findByItemId(Long itemId);

    @Update("UPDATE trace_event SET chain_status = #{chainStatus}, update_time = NOW() WHERE id = #{id}")
    int updateChainStatus(@Param("id") Long id, @Param("chainStatus") String chainStatus);
}
