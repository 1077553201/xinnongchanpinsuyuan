package org.nongchanpin.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.*;

@Mapper
public interface GrowingMapper {
    @Select("SELECT id, organization_id, username, display_name FROM sys_user u WHERE u.status = 'ACTIVE' "
            + "AND EXISTS (SELECT 1 FROM sys_organization o WHERE o.id=u.organization_id AND o.status='ACTIVE') "
            + "AND (#{org} IS NULL OR u.organization_id = #{org}) AND EXISTS (SELECT 1 FROM sys_user_role ur "
            + "JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.status = 'ACTIVE' AND r.role_code IN ('ADMIN','MANAGER')) ORDER BY u.id")
    List<ProductionPerson> people(@Param("org") Long org);
    @Select("SELECT * FROM growing_environment_rule WHERE cycle_id = #{cycle} ORDER BY (effective_to IS NULL) DESC, effective_from DESC, id DESC LIMIT 200")
    List<GrowingRuleEntity> rules(Long cycle);
    @Select("SELECT * FROM growing_environment_rule WHERE request_key = #{key}")
    GrowingRuleEntity ruleByRequest(String key);
    @Select("SELECT * FROM growing_environment_rule WHERE id = #{id}")
    GrowingRuleEntity rule(Long id);
    @Update("UPDATE growing_environment_rule SET effective_to = #{at} WHERE cycle_id = #{cycle} AND metric = #{metric} AND effective_to IS NULL")
    int retireMetric(@Param("cycle") Long cycle, @Param("metric") String metric, @Param("at") LocalDateTime at);
    @Update("UPDATE growing_environment_rule SET effective_to = #{at} WHERE id = #{id} AND effective_to IS NULL")
    int retireRule(@Param("id") Long id, @Param("at") LocalDateTime at);
    @Update("UPDATE growing_environment_rule SET effective_to = #{at} WHERE cycle_id = #{cycle} AND effective_to IS NULL")
    int retireCycleRules(@Param("cycle") Long cycle, @Param("at") LocalDateTime at);
    @Insert("INSERT INTO growing_environment_rule (cycle_id, metric, min_value, max_value, unit, expected_interval_minutes, basis, notes, effective_from, created_by, request_key, request_hash) "
            + "VALUES (#{cycleId}, #{metric}, #{minValue}, #{maxValue}, #{unit}, #{expectedIntervalMinutes}, #{basis}, #{notes}, #{effectiveFrom}, #{createdBy}, #{requestKey}, #{requestHash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRule(GrowingRuleEntity rule);
    @Select("SELECT * FROM growing_environment_rule WHERE cycle_id = #{cycle} AND metric = #{metric} "
            + "AND effective_from <= #{at} AND (effective_to IS NULL OR effective_to > #{at}) FOR SHARE")
    List<GrowingRuleEntity> rulesAt(@Param("cycle") Long cycle, @Param("metric") String metric, @Param("at") LocalDateTime at);
    @Select("SELECT MAX(r.reported_at) FROM environment_assessment a JOIN iot_sensor_reading r ON r.id = a.reading_id WHERE a.rule_id = #{id}")
    LocalDateTime latestRuleReading(Long id);
    @Insert("INSERT INTO environment_assessment (reading_id, cycle_id, rule_id, metric, result, reason) "
            + "VALUES (#{readingId}, #{cycleId}, #{ruleId}, #{metric}, #{result}, #{reason})")
    int insertAssessment(EnvironmentAssessmentEntity assessment);
    @Insert("INSERT INTO environment_case (reading_id, cycle_id) VALUES (#{readingId}, #{cycleId})")
    int insertCase(EnvironmentAssessmentEntity assessment);

    String ASSESSMENT_VIEW = "SELECT a.*, r.value, r.unit, r.device_code, r.reported_at, g.min_value, g.max_value, g.unit AS rule_unit, "
            + "c.id AS case_id, c.status AS case_status, c.action_taken, c.observation, c.handled_at, c.handled_by, u.display_name AS handler_name "
            + "FROM environment_assessment a JOIN iot_sensor_reading r ON r.id = a.reading_id LEFT JOIN growing_environment_rule g ON g.id = a.rule_id "
            + "LEFT JOIN environment_case c ON c.reading_id = a.reading_id LEFT JOIN sys_user u ON u.id = c.handled_by ";
    @Select(ASSESSMENT_VIEW + "WHERE a.cycle_id = #{cycle} ORDER BY r.reported_at DESC, r.id DESC LIMIT 100")
    List<EnvironmentAssessmentEntity> assessments(Long cycle);
    @Select(ASSESSMENT_VIEW + "WHERE a.cycle_id = #{cycle} AND c.id IS NOT NULL ORDER BY (c.status = 'OPEN') DESC, c.id DESC LIMIT 100")
    List<EnvironmentAssessmentEntity> cases(Long cycle);
    @Select(ASSESSMENT_VIEW + "WHERE c.id = #{id}")
    EnvironmentAssessmentEntity caseById(Long id);
    @Update("UPDATE environment_case SET status = 'HANDLED', handled_by = #{actor}, handled_at = #{at}, action_taken = #{action}, observation = #{observation} WHERE id = #{id} AND status = 'OPEN'")
    int handleCase(@Param("id") Long id, @Param("actor") Long actor, @Param("at") LocalDateTime at, @Param("action") String action, @Param("observation") String observation);
    @Select("SELECT COUNT(*) FROM environment_case WHERE cycle_id = #{cycle} AND status = 'OPEN'")
    int openCases(Long cycle);

    @Select("SELECT * FROM farm_task WHERE cycle_id = #{cycle} ORDER BY (status = 'PLANNED') DESC, IF(status = 'PLANNED', planned_on, NULL) ASC, id DESC LIMIT 200")
    List<FarmTaskEntity> tasks(Long cycle);
    @Select("SELECT * FROM farm_task WHERE id = #{id}")
    FarmTaskEntity task(Long id);
    @Select("SELECT * FROM farm_task WHERE id = #{id} FOR UPDATE")
    FarmTaskEntity lockTask(Long id);
    @Insert("INSERT INTO farm_task (cycle_id, task_code, task_type, title, planned_on, assigned_to, assignee_name, instructions, created_by) "
            + "VALUES (#{cycleId}, #{taskCode}, #{taskType}, #{title}, #{plannedOn}, #{assignedTo}, #{assigneeName}, #{instructions}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTask(FarmTaskEntity task);
    @Select("SELECT COUNT(*) FROM farm_task WHERE cycle_id = #{cycle} AND status = 'PLANNED'")
    int pendingTasks(Long cycle);
    @Update("UPDATE farm_task SET status = 'CANCELLED', cancelled_by = #{actor}, cancelled_at = #{at}, cancel_reason = #{reason} WHERE id = #{id} AND status = 'PLANNED'")
    int cancelTask(@Param("id") Long id, @Param("actor") Long actor, @Param("at") LocalDateTime at, @Param("reason") String reason);
    @Update("UPDATE farm_task SET status = 'COMPLETED' WHERE id = #{id} AND status = 'PLANNED'")
    int completeTask(Long id);
    @Select("SELECT * FROM farm_task_execution WHERE task_id = #{id}")
    FarmExecutionEntity execution(Long id);
    @Select("SELECT * FROM farm_task_execution WHERE request_key = #{key}")
    FarmExecutionEntity executionByRequest(String key);
    @Insert("INSERT INTO farm_task_execution (task_id, performed_by, performer_name, performed_at, input_name, quantity, unit, notes, evidence_reference, recorded_by, recorded_name, request_key, request_hash) "
            + "VALUES (#{taskId}, #{performedBy}, #{performerName}, #{performedAt}, #{inputName}, #{quantity}, #{unit}, #{notes}, #{evidenceReference}, #{recordedBy}, #{recordedName}, #{requestKey}, #{requestHash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExecution(FarmExecutionEntity execution);
    @Select("SELECT DATE(MAX(e.performed_at)) FROM farm_task_execution e JOIN farm_task t ON t.id = e.task_id WHERE t.cycle_id = #{cycle}")
    LocalDate latestExecutionDate(Long cycle);
}
