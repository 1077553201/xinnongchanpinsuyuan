package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.IotDeviceEntity;

@Mapper
public interface IotDeviceMapper {
    @Insert("INSERT INTO iot_device (gateway_id, farm_id, gateway_code, device_code, device_name, device_type, status, last_seen_at) "
            + "VALUES (#{gatewayId}, #{farmId}, #{gatewayCode}, #{deviceCode}, #{deviceName}, #{deviceType}, #{status}, #{lastSeenAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IotDeviceEntity device);

    @Select("SELECT * FROM iot_device WHERE device_code = #{deviceCode}")
    IotDeviceEntity findByCode(String deviceCode);

    @Select("SELECT * FROM iot_device WHERE status <> 'DELETED' ORDER BY id DESC")
    List<IotDeviceEntity> findAll();

    @Update("UPDATE iot_device SET last_seen_at = #{lastSeenAt}, status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateLastSeen(@Param("id") Long id, @Param("lastSeenAt") LocalDateTime lastSeenAt,
            @Param("status") String status);
}
