package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.GrowingMapper;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.dto.GrowingRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@org.springframework.validation.annotation.Validated
@RequiredArgsConstructor
public class FarmTaskService {
    private final GrowingMapper growing;
    private final ProductionMapper production;
    private final GrowingAccess access;
    private final AuditService audit;
    private final PayloadService payloads;

    public List<ProductionPerson> people() { return growing.people(AccessContext.organization()); }
    public List<FarmTaskEntity> tasks(Long cycleId) { access.cycle(cycleId, false); return growing.tasks(cycleId); }
    public FarmExecutionEntity execution(Long taskId) {
        FarmTaskEntity task = requireTask(taskId); access.cycle(task.getCycleId(), false); return growing.execution(taskId);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FarmTaskEntity create(Long cycleId, @javax.validation.Valid GrowingRequests.Task request) {
        PlantingCycleEntity cycle = access.cycle(cycleId, true); access.active(cycle);
        if (request.getPlannedOn().isBefore(cycle.getPlantedOn())) throw new IllegalArgumentException("计划作业日期不能早于本轮种植日期。");
        Long org = production.plot(cycle.getPlotId()).getOrganizationId();
        ProductionPerson assignee = growing.people(org).stream().filter(person -> person.getId().equals(request.getAssignedTo())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("请选择同机构有效的生产人员或管理员。"));
        FarmTaskEntity task = new FarmTaskEntity(); task.setCycleId(cycleId); task.setTaskCode(request.getTaskCode()); task.setTaskType(request.getTaskType());
        task.setTitle(request.getTitle().trim()); task.setPlannedOn(request.getPlannedOn()); task.setAssignedTo(assignee.getId()); task.setAssigneeName(assignee.getDisplayName());
        task.setInstructions(text(request.getInstructions())); task.setCreatedBy(AccessContext.require().getId()); task.setStatus("PLANNED");
        growing.insertTask(task); audit.record("FARM_TASK_CREATE", "FARM_TASK", task.getId(), task.getTaskCode());
        return growing.task(task.getId());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FarmTaskEntity cancel(Long id, @javax.validation.Valid GrowingRequests.Reason request) {
        FarmTaskEntity task = requireTask(id); access.active(access.cycle(task.getCycleId(), true));
        if (growing.cancelTask(id, AccessContext.require().getId(), LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), request.getReason().trim()) != 1) {
            throw new IllegalArgumentException("只能取消待执行任务，请刷新核对状态。");
        }
        audit.record("FARM_TASK_CANCEL", "FARM_TASK", id, request.getReason().trim()); return growing.task(id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FarmExecutionEntity complete(Long id, @javax.validation.Valid GrowingRequests.Execution request) {
        FarmTaskEntity initial = requireTask(id); PlantingCycleEntity cycle = access.cycle(initial.getCycleId(), true);
        FarmTaskEntity task = growing.lockTask(id);
        Actor actor = AccessContext.require();
        if (!actor.isAdmin() && !actor.getId().equals(task.getAssignedTo())) throw new AccessException(403, "只有任务负责人或管理员可以登记实际完成。");
        Map<String, Object> content = payloads.map();
        content.put("task", id); content.put("recorder", actor.getId()); content.put("performedAt", request.getPerformedAt());
        content.put("input", text(request.getInputName())); content.put("quantity", request.getQuantity() == null ? null : request.getQuantity().stripTrailingZeros().toPlainString());
        content.put("unit", text(request.getUnit())); content.put("notes", request.getNotes().trim()); content.put("evidence", text(request.getEvidenceReference()));
        String hash = payloads.requestHash(content);
        FarmExecutionEntity existing = growing.executionByRequest(request.getRequestKey());
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) throw new IllegalArgumentException("操作标识已用于其他内容，请刷新核对。");
            return existing;
        }
        access.active(cycle);
        if (!"PLANNED".equals(task.getStatus())) throw new IllegalArgumentException("任务已完成或已取消，不能重复登记。");
        if (request.getPerformedAt().isAfter(LocalDateTime.now()) || request.getPerformedAt().toLocalDate().isBefore(cycle.getPlantedOn())) {
            throw new IllegalArgumentException("实际执行时间须在本轮种植开始之后，且不能晚于当前时间。");
        }
        boolean inputRequired = List.of("FERTILIZATION", "PEST_CONTROL").contains(task.getTaskType());
        if (inputRequired && (text(request.getInputName()).isEmpty() || request.getQuantity() == null || text(request.getUnit()).isEmpty())) {
            throw new IllegalArgumentException("施肥或用药作业须登记投入品名称、实际用量和单位。");
        }
        if (request.getQuantity() != null && (request.getQuantity().signum() <= 0 || text(request.getUnit()).isEmpty())) throw new IllegalArgumentException("用量须为正数，并填写单位。");
        if (request.getQuantity() == null && !text(request.getUnit()).isEmpty()) throw new IllegalArgumentException("填写用量单位时，请同时填写实际用量。");
        FarmExecutionEntity record = new FarmExecutionEntity(); record.setTaskId(id); record.setPerformedBy(task.getAssignedTo()); record.setPerformerName(task.getAssigneeName());
        record.setPerformedAt(request.getPerformedAt()); record.setInputName(text(request.getInputName())); record.setQuantity(request.getQuantity()); record.setUnit(text(request.getUnit()));
        record.setNotes(request.getNotes().trim()); record.setEvidenceReference(text(request.getEvidenceReference())); record.setRecordedBy(actor.getId()); record.setRecordedName(actor.getDisplayName());
        record.setRequestKey(request.getRequestKey()); record.setRequestHash(hash);
        growing.insertExecution(record);
        if (growing.completeTask(id) != 1) throw new IllegalArgumentException("任务状态已变化，请刷新核对。");
        audit.record("FARM_TASK_COMPLETE", "FARM_TASK", id, "实际执行人 " + record.getPerformerName() + " / 执行记录 " + record.getId());
        return growing.execution(id);
    }
    private FarmTaskEntity requireTask(Long id) { FarmTaskEntity task = growing.task(id); if (task == null) throw new IllegalArgumentException("农事任务不存在。"); return task; }
    private String text(String value) { return value == null ? "" : value.trim(); }
}
