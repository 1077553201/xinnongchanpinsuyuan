package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.IotSensorReadingEntity;

@Mapper
public interface IotSensorReadingMapper {
    @Update("UPDATE iot_sensor_reading SET batch_id = NULL WHERE id = #{id}")
    int clearUnverifiedBatch(Long id);
    @Select({"<script>", "SELECT r.*, p.binding_id, p.plot_id, p.cycle_id, p.resolution, p.reason AS resolution_reason "
            + "FROM iot_sensor_reading r LEFT JOIN reading_provenance p ON p.reading_id = r.id WHERE 1 = 1",
            "<if test='deviceCode != null'>AND r.device_code = #{deviceCode}</if>",
            "<if test='metric != null'>AND r.metric = #{metric}</if>",
            "ORDER BY r.reported_at DESC, r.id DESC LIMIT #{limit}", "</script>"})
    List<IotSensorReadingEntity> findFiltered(@Param("deviceCode") String deviceCode, @Param("metric") String metric,
            @Param("limit") int limit);

    @Insert("INSERT INTO iot_sensor_reading (farm_id, gateway_code, device_code, batch_id, metric, value, unit, signature, reported_at, status) "
            + "VALUES (#{farmId}, #{gatewayCode}, #{deviceCode}, #{batchId}, #{metric}, #{value}, #{unit}, #{signature}, #{reportedAt}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IotSensorReadingEntity reading);

    @Select("SELECT r.*, p.binding_id, p.plot_id, p.cycle_id, p.resolution, p.reason AS resolution_reason "
            + "FROM iot_sensor_reading r LEFT JOIN reading_provenance p ON p.reading_id = r.id "
            + "WHERE r.device_code = #{deviceCode} AND r.metric = #{metric} "
            + "AND r.reported_at >= #{start} AND r.reported_at < #{end} AND r.status = 'RAW' "
            + "AND (p.reading_id IS NULL OR p.resolution = 'BOUND') ORDER BY r.reported_at ASC, r.id ASC")
    List<IotSensorReadingEntity> findInWindow(@Param("deviceCode") String deviceCode,
            @Param("metric") String metric, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT * FROM iot_sensor_reading WHERE device_code = #{deviceCode} AND metric = #{metric} "
            + "ORDER BY reported_at DESC LIMIT #{limit}")
    List<IotSensorReadingEntity> findRecent(@Param("deviceCode") String deviceCode,
            @Param("metric") String metric, @Param("limit") int limit);

    @Select("SELECT * FROM iot_sensor_reading ORDER BY reported_at DESC LIMIT #{limit}")
    List<IotSensorReadingEntity> findLatest(@Param("limit") int limit);
}
