package org.nongchanpin.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.*;

@Mapper
public interface HarvestMapper {
    String HARVEST_VIEW = "SELECT h.*, COALESCE((SELECT SUM(g.weight_kg) FROM harvest_grade g WHERE g.harvest_id=h.id),0) AS allocated_kg, "
            + "COALESCE((SELECT SUM(g.weight_kg) FROM harvest_grade g WHERE g.harvest_id=h.id AND g.kind='LOSS'),0) AS loss_kg, "
            + "COALESCE((SELECT SUM(p.total_weight_kg) FROM packing_lot p JOIN harvest_grade g ON g.id=p.grade_id WHERE g.harvest_id=h.id),0) AS packed_kg FROM harvest_lot h ";
    String GRADE_VIEW = "SELECT g.*, COALESCE((SELECT SUM(p.total_weight_kg) FROM packing_lot p WHERE p.grade_id=g.id),0) AS packed_kg FROM harvest_grade g ";
    @Select(HARVEST_VIEW + "WHERE h.cycle_id=#{cycle} ORDER BY (h.status='OPEN') DESC,h.id DESC LIMIT 200")
    List<HarvestLotEntity> harvests(Long cycle);
    @Select(HARVEST_VIEW + "WHERE h.id=#{id}") HarvestLotEntity harvest(Long id);
    @Select("SELECT * FROM harvest_lot WHERE id=#{id} FOR UPDATE") HarvestLotEntity lockHarvest(Long id);
    @Select("SELECT * FROM harvest_lot WHERE request_key=#{key}") HarvestLotEntity harvestByRequest(String key);
    @Select(GRADE_VIEW + "WHERE g.harvest_id=#{id} ORDER BY g.id DESC LIMIT 200") List<HarvestGradeEntity> grades(Long id);
    @Select(GRADE_VIEW + "WHERE g.id=#{id}") HarvestGradeEntity grade(Long id);
    @Select("SELECT * FROM harvest_grade WHERE request_key=#{key}") HarvestGradeEntity gradeByRequest(String key);
    @Select("SELECT p.*,g.harvest_id,g.grade_name FROM packing_lot p JOIN harvest_grade g ON g.id=p.grade_id WHERE g.harvest_id=#{id} ORDER BY p.id DESC LIMIT 200")
    List<PackingLotEntity> packings(Long id);
    @Select("SELECT * FROM packing_lot WHERE request_key=#{key}") PackingLotEntity packingByRequest(String key);
    @Select("SELECT * FROM packing_lot WHERE id=#{id}") PackingLotEntity packing(Long id);
    @Select("SELECT COALESCE(SUM(weight_kg),0) FROM harvest_grade WHERE harvest_id=#{id}") BigDecimal allocated(Long id);
    @Select("SELECT COALESCE(SUM(total_weight_kg),0) FROM packing_lot WHERE grade_id=#{id}") BigDecimal packed(Long id);
    @Select("SELECT COUNT(*) FROM harvest_grade WHERE harvest_id=#{id}") int gradeCount(Long id);
    @Select("SELECT COUNT(*) FROM harvest_lot WHERE cycle_id=#{id} AND status='OPEN'") int openHarvests(Long id);
    @Select("SELECT DATE(MAX(harvested_at)) FROM harvest_lot WHERE cycle_id=#{id}") LocalDate latestHarvestDate(Long id);
    @Update("UPDATE harvest_lot SET status='GRADED' WHERE id=#{id} AND status='OPEN'") int finish(Long id);
    @Insert("INSERT INTO harvest_lot (cycle_id,lot_code,harvested_at,quantity,unit,weight_kg,performer_name,notes,recorded_by,recorder_name,request_key,request_hash) "
            + "VALUES (#{cycleId},#{lotCode},#{harvestedAt},#{quantity},#{unit},#{weightKg},#{performerName},#{notes},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertHarvest(HarvestLotEntity record);
    @Insert("INSERT INTO harvest_grade (harvest_id,kind,grade_name,weight_kg,handled_at,performer_name,location,reason,recorded_by,recorder_name,request_key,request_hash) "
            + "VALUES (#{harvestId},#{kind},#{gradeName},#{weightKg},#{handledAt},#{performerName},#{location},#{reason},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertGrade(HarvestGradeEntity record);
    @Insert("INSERT INTO packing_lot (grade_id,packing_code,packed_at,package_count,package_unit,net_weight_kg,total_weight_kg,performer_name,location,notes,recorded_by,recorder_name,request_key,request_hash) "
            + "VALUES (#{gradeId},#{packingCode},#{packedAt},#{packageCount},#{packageUnit},#{netWeightKg},#{totalWeightKg},#{performerName},#{location},#{notes},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertPacking(PackingLotEntity record);
}
