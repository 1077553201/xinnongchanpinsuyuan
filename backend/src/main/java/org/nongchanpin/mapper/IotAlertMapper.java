package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.nongchanpin.model.entity.IotAlertEntity;

@Mapper
public interface IotAlertMapper {
    @Insert("INSERT INTO iot_alert (farm_id, gateway_code, device_code, metric, alert_type, level, value, "
            + "threshold_min, threshold_max, message, occurred_at, status) "
            + "VALUES (#{farmId}, #{gatewayCode}, #{deviceCode}, #{metric}, #{alertType}, #{level}, #{value}, "
            + "#{thresholdMin}, #{thresholdMax}, #{message}, #{occurredAt}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IotAlertEntity alert);

    @Select("SELECT * FROM iot_alert WHERE status = 'OPEN' ORDER BY occurred_at DESC LIMIT #{limit}")
    List<IotAlertEntity> findOpen(@Param("limit") int limit);

    @Select("SELECT * FROM iot_alert ORDER BY occurred_at DESC LIMIT #{limit}")
    List<IotAlertEntity> findLatest(@Param("limit") int limit);

    @Select("SELECT * FROM iot_alert WHERE device_code = #{deviceCode} ORDER BY occurred_at DESC LIMIT #{limit}")
    List<IotAlertEntity> findByDevice(@Param("deviceCode") String deviceCode, @Param("limit") int limit);
}
