package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.nongchanpin.model.entity.IotGatewayEntity;

@Mapper
public interface IotGatewayMapper {
    @Insert("INSERT INTO iot_gateway (farm_id, gateway_code, gateway_name, mqtt_client_id, status) "
            + "VALUES (#{farmId}, #{gatewayCode}, #{gatewayName}, #{mqttClientId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IotGatewayEntity gateway);

    @Select("SELECT * FROM iot_gateway WHERE gateway_code = #{gatewayCode}")
    IotGatewayEntity findByCode(String gatewayCode);

    @Select("SELECT * FROM iot_gateway WHERE status <> 'DELETED' ORDER BY id DESC")
    List<IotGatewayEntity> findAll();
}
