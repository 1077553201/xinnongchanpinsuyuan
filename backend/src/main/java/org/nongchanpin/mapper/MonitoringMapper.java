package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.*;

@Mapper
public interface MonitoringMapper {
    @Select("SELECT id FROM iot_device WHERE device_code = #{code} FOR UPDATE")
    Long lockDeviceForAggregation(String code);
    @Select("SELECT d.*, o.organization_id FROM iot_device d LEFT JOIN device_ownership o ON o.device_id = d.id "
            + "WHERE d.status <> 'DELETED' AND (#{orgId} IS NULL OR o.organization_id = #{orgId}) ORDER BY d.id DESC")
    List<IotDeviceEntity> devices(@Param("orgId") Long orgId);
    @Select("SELECT d.*, o.organization_id FROM iot_device d LEFT JOIN device_ownership o ON o.device_id = d.id WHERE d.id = #{id} FOR UPDATE")
    IotDeviceEntity lockDevice(Long id);
    @Insert("INSERT INTO device_ownership (device_id, organization_id, assigned_by) VALUES (#{device}, #{org}, #{actor})")
    int assign(@Param("device") Long device, @Param("org") Long org, @Param("actor") Long actor);
    @Select("SELECT b.*, d.device_code, d.device_name, p.plot_name, c.cycle_code FROM monitoring_binding b "
            + "JOIN iot_device d ON d.id = b.device_id JOIN farm_plot p ON p.id = b.plot_id "
            + "JOIN planting_cycle c ON c.id = b.cycle_id WHERE (#{org} IS NULL OR p.organization_id = #{org}) ORDER BY b.id DESC")
    List<MonitoringBindingEntity> bindings(@Param("org") Long org);
    @Select("SELECT * FROM monitoring_binding WHERE id = #{id}")
    MonitoringBindingEntity binding(Long id);
    @Select("SELECT COUNT(*) FROM monitoring_binding WHERE device_id = #{id} AND ended_at IS NULL")
    int activeBindings(Long id);
    @Insert("INSERT INTO monitoring_binding (device_id, plot_id, cycle_id, gateway_code, farm_id, started_at, created_by) "
            + "VALUES (#{deviceId}, #{plotId}, #{cycleId}, #{gatewayCode}, #{farmId}, #{startedAt}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBinding(MonitoringBindingEntity binding);
    @Update("UPDATE monitoring_binding SET ended_at = #{endedAt}, ended_by = #{endedBy}, end_reason = #{endReason} WHERE id = #{id} AND ended_at IS NULL")
    int close(MonitoringBindingEntity binding);
    @Select("SELECT * FROM monitoring_binding WHERE device_id = #{device} AND started_at <= #{at} "
            + "AND (ended_at IS NULL OR ended_at > #{at}) FOR SHARE")
    List<MonitoringBindingEntity> atTime(@Param("device") Long device, @Param("at") LocalDateTime at);
    @Insert("INSERT INTO reading_provenance (reading_id, binding_id, plot_id, cycle_id, resolution, reason) "
            + "VALUES (#{id}, #{bindingId}, #{plotId}, #{cycleId}, #{resolution}, #{resolutionReason})")
    int attribute(IotSensorReadingEntity reading);
    @Insert("INSERT INTO window_provenance (window_id, binding_id, plot_id, cycle_id) VALUES (#{id}, #{bindingId}, #{plotId}, #{cycleId})")
    int attributeWindow(IotDataWindowEntity window);
    @Select("SELECT r.*, p.binding_id, p.plot_id, p.cycle_id, p.resolution, p.reason AS resolution_reason FROM iot_sensor_reading r "
            + "JOIN reading_provenance p ON p.reading_id = r.id WHERE p.cycle_id = #{cycle} AND r.status = 'RAW' ORDER BY r.reported_at DESC, r.id DESC LIMIT 100")
    List<IotSensorReadingEntity> readings(Long cycle);
    @Select("SELECT w.*, p.binding_id, p.plot_id, p.cycle_id FROM iot_data_window w JOIN window_provenance p ON p.window_id = w.id "
            + "WHERE p.cycle_id = #{cycle} AND NOT EXISTS (SELECT 1 FROM iot_data_window n JOIN window_provenance np ON np.window_id = n.id "
            + "WHERE np.cycle_id = p.cycle_id AND np.binding_id = p.binding_id AND n.device_code = w.device_code "
            + "AND n.metric = w.metric AND n.window_start = w.window_start AND n.window_end = w.window_end AND n.id > w.id) "
            + "ORDER BY w.window_end DESC, w.id DESC LIMIT 50")
    List<IotDataWindowEntity> windows(Long cycle);
}
