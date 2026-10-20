package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.IotDataWindowEntity;

@Mapper
public interface IotDataWindowMapper {
    @Select("SELECT * FROM iot_data_window WHERE status = 'ACTIVE' ORDER BY window_end DESC LIMIT 20")
    List<IotDataWindowEntity> findRecentActive();

    @Select("SELECT * FROM iot_data_window WHERE batch_id = #{batchId} AND status = 'ACTIVE' ORDER BY window_end DESC LIMIT 20")
    List<IotDataWindowEntity> findRecentForBatch(@Param("batchId") Long batchId);

    @Insert("INSERT INTO iot_data_window (farm_id, batch_id, gateway_code, device_code, metric, window_start, "
            + "window_end, min_value, max_value, avg_value, sample_count, data_hash, chain_status, status) "
            + "VALUES (#{farmId}, #{batchId}, #{gatewayCode}, #{deviceCode}, #{metric}, #{windowStart}, "
            + "#{windowEnd}, #{minValue}, #{maxValue}, #{avgValue}, #{sampleCount}, #{dataHash}, #{chainStatus}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IotDataWindowEntity window);

    @Select("SELECT * FROM iot_data_window WHERE data_hash = #{dataHash}")
    IotDataWindowEntity findByDataHash(String dataHash);

    @Select("SELECT * FROM iot_data_window WHERE device_code = #{deviceCode} ORDER BY window_start DESC LIMIT #{limit}")
    List<IotDataWindowEntity> findByDevice(@Param("deviceCode") String deviceCode, @Param("limit") int limit);

    @Select("SELECT * FROM iot_data_window ORDER BY window_start DESC LIMIT #{limit}")
    List<IotDataWindowEntity> findLatest(@Param("limit") int limit);

    @Update("UPDATE iot_data_window SET chain_status = #{chainStatus}, update_time = NOW() WHERE id = #{id}")
    int updateChainStatus(@Param("id") Long id, @Param("chainStatus") String chainStatus);
}
