package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.nongchanpin.model.entity.IotSensorReadingEntity;

@Mapper
public interface IotSensorReadingMapper {
    @Select({"<script>", "SELECT * FROM iot_sensor_reading WHERE 1 = 1",
            "<if test='deviceCode != null'>AND device_code = #{deviceCode}</if>",
            "<if test='metric != null'>AND metric = #{metric}</if>",
            "ORDER BY reported_at DESC LIMIT #{limit}", "</script>"})
    List<IotSensorReadingEntity> findFiltered(@Param("deviceCode") String deviceCode, @Param("metric") String metric,
            @Param("limit") int limit);

    @Insert("INSERT INTO iot_sensor_reading (farm_id, gateway_code, device_code, batch_id, metric, value, unit, signature, reported_at, status) "
            + "VALUES (#{farmId}, #{gatewayCode}, #{deviceCode}, #{batchId}, #{metric}, #{value}, #{unit}, #{signature}, #{reportedAt}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IotSensorReadingEntity reading);

    @Select("SELECT * FROM iot_sensor_reading WHERE device_code = #{deviceCode} AND metric = #{metric} "
            + "AND reported_at >= #{start} AND reported_at < #{end} ORDER BY reported_at ASC")
    List<IotSensorReadingEntity> findInWindow(@Param("deviceCode") String deviceCode,
            @Param("metric") String metric, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT * FROM iot_sensor_reading WHERE device_code = #{deviceCode} AND metric = #{metric} "
            + "ORDER BY reported_at DESC LIMIT #{limit}")
    List<IotSensorReadingEntity> findRecent(@Param("deviceCode") String deviceCode,
            @Param("metric") String metric, @Param("limit") int limit);

    @Select("SELECT * FROM iot_sensor_reading ORDER BY reported_at DESC LIMIT #{limit}")
    List<IotSensorReadingEntity> findLatest(@Param("limit") int limit);
}
