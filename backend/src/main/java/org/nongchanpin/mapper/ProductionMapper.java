package org.nongchanpin.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.FarmPlotEntity;
import org.nongchanpin.model.entity.PlantingCycleEntity;

@Mapper
public interface ProductionMapper {
    @Select("SELECT c.id,CONCAT_WS(' · ',p.plot_name,c.cycle_code,c.product_name,c.variety) AS label FROM planting_cycle c JOIN farm_plot p ON p.id=c.plot_id WHERE c.status='ACTIVE' AND (#{org} IS NULL OR p.organization_id=#{org}) AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',p.plot_name,c.cycle_code,c.product_name,c.variety))>0) ORDER BY c.id DESC LIMIT 50")
    List<Map<String,Object>> agentCycles(@Param("org")Long org,@Param("search")String search);
    @Select("SELECT id, org_name AS name, org_code AS code, status FROM sys_organization WHERE status = 'ACTIVE' "
            + "AND (#{orgId} IS NULL OR id = #{orgId}) ORDER BY id")
    List<Map<String, Object>> organizations(@Param("orgId") Long orgId);

    @Select("SELECT id,org_name AS name,org_code AS code,status FROM sys_organization WHERE status<>'DELETED' AND (#{orgId} IS NULL OR id=#{orgId}) ORDER BY id")
    List<Map<String, Object>> organizationNames(@Param("orgId") Long orgId);

    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT id FROM sys_organization WHERE id=#{id} FOR UPDATE")
    Long lockOrganization(Long id);

    @Select("SELECT COUNT(*) FROM sys_organization WHERE id = #{id} AND status = 'ACTIVE'")
    int activeOrganization(Long id);

    @Select("SELECT * FROM farm_plot WHERE (#{orgId} IS NULL OR organization_id = #{orgId}) ORDER BY id DESC")
    List<FarmPlotEntity> plots(@Param("orgId") Long orgId);

    @Select("SELECT * FROM farm_plot WHERE id = #{id}")
    FarmPlotEntity plot(Long id);

    @Select("SELECT * FROM farm_plot WHERE id = #{id} FOR UPDATE")
    FarmPlotEntity lockPlot(Long id);

    @Insert("INSERT INTO farm_plot (organization_id, plot_code, plot_name, location, area_mu, manager_name, notes) "
            + "VALUES (#{organizationId}, #{plotCode}, #{plotName}, #{location}, #{areaMu}, #{managerName}, #{notes})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPlot(FarmPlotEntity plot);

    @Select("SELECT c.* FROM planting_cycle c JOIN farm_plot p ON p.id = c.plot_id "
            + "WHERE (#{orgId} IS NULL OR p.organization_id = #{orgId}) ORDER BY c.planted_on DESC, c.id DESC")
    List<PlantingCycleEntity> cycles(@Param("orgId") Long orgId);

    @Select("SELECT COUNT(*) FROM monitoring_binding WHERE cycle_id = #{id} AND ended_at IS NULL")
    int openBindings(Long id);

    @Select("SELECT DATE(MAX(ended_at)) FROM monitoring_binding WHERE cycle_id = #{id}")
    java.time.LocalDate latestMonitoringDate(Long id);

    @Select("SELECT * FROM planting_cycle WHERE id = #{id}")
    PlantingCycleEntity cycle(Long id);

    @Select("SELECT * FROM planting_cycle WHERE id = #{id} FOR UPDATE")
    PlantingCycleEntity lockCycle(Long id);

    @Select("SELECT COUNT(*) FROM planting_cycle WHERE plot_id = #{id} AND status = 'ACTIVE'")
    int activeCycles(Long id);

    @Select("SELECT COUNT(*) FROM planting_cycle WHERE plot_id = #{plotId} "
            + "AND ended_on > #{plantedOn}")
    int laterHistory(PlantingCycleEntity cycle);

    @Insert("INSERT INTO planting_cycle (plot_id, product_id, cycle_code, product_name, variety, planted_on, "
            + "expected_harvest_on, manager_name, environment_notes, status) VALUES (#{plotId}, #{productId}, "
            + "#{cycleCode}, #{productName}, #{variety}, #{plantedOn}, #{expectedHarvestOn}, #{managerName}, "
            + "#{environmentNotes}, 'ACTIVE')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCycle(PlantingCycleEntity cycle);

    @Update("UPDATE planting_cycle SET status = 'ENDED', ended_on = #{endedOn}, end_reason = #{endReason} "
            + "WHERE id = #{id} AND status = 'ACTIVE'")
    int endCycle(PlantingCycleEntity cycle);
}
