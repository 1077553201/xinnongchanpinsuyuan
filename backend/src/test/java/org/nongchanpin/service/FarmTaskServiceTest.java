package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.GrowingRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;

class FarmTaskServiceTest {
    private final GrowingMapper growing = mock(GrowingMapper.class);
    private final ProductionMapper production = mock(ProductionMapper.class);
    private final FarmTaskService service = new FarmTaskService(growing, production, new GrowingAccess(production), mock(AuditService.class), new PayloadService());
    private final FarmTaskEntity task = new FarmTaskEntity();
    private final PlantingCycleEntity cycle = new PlantingCycleEntity();
    @BeforeEach void setup() {
        Actor actor = new Actor(); actor.setId(10L); actor.setOrganizationId(20L); actor.setDisplayName("记录人"); actor.setRole("MANAGER"); AccessContext.set(actor);
        cycle.setId(1L); cycle.setPlotId(2L); cycle.setStatus("ACTIVE"); cycle.setPlantedOn(LocalDate.now().minusDays(30));
        when(production.cycle(1L)).thenReturn(cycle); when(production.lockCycle(1L)).thenReturn(cycle);
        FarmPlotEntity plot = new FarmPlotEntity(); plot.setOrganizationId(20L); when(production.plot(2L)).thenReturn(plot);
        task.setId(3L); task.setCycleId(1L); task.setStatus("PLANNED"); task.setTaskType("FERTILIZATION"); task.setAssignedTo(10L); task.setAssigneeName("执行人");
        when(growing.task(3L)).thenReturn(task); when(growing.lockTask(3L)).thenReturn(task); when(growing.completeTask(3L)).thenReturn(1);
    }
    @AfterEach void cleanup() { AccessContext.clear(); }
    private GrowingRequests.Execution draft() {
        GrowingRequests.Execution request = new GrowingRequests.Execution(); request.setPerformedAt(LocalDateTime.now().minusMinutes(1));
        request.setInputName("联调投入品"); request.setQuantity(new BigDecimal("2")); request.setUnit("kg"); request.setNotes("记录实际操作，不是施用建议");
        request.setRequestKey("00000000-0000-4000-8000-000000000001"); return request;
    }
    @Test void plannedTaskDoesNotCreateExecutionRecord() {
        ProductionPerson person = new ProductionPerson(); person.setId(10L); person.setDisplayName("执行人"); when(growing.people(20L)).thenReturn(List.of(person));
        GrowingRequests.Task request = new GrowingRequests.Task(); request.setTaskCode("QA-TASK"); request.setTaskType("IRRIGATION"); request.setTitle("灌溉"); request.setPlannedOn(LocalDate.now()); request.setAssignedTo(10L);
        service.create(1L, request); verify(growing).insertTask(any()); verify(growing, never()).insertExecution(any()); verify(growing, never()).completeTask(any());
    }
    @Test void fertilizerCompletionRequiresActualInputAndQuantity() {
        GrowingRequests.Execution request = draft(); request.setQuantity(null); assertThrows(IllegalArgumentException.class, () -> service.complete(3L, request));
        verify(growing, never()).insertExecution(any());
    }
    @Test void completionPreservesPerformerAndRecorderAndRetriesAreIdempotent() {
        AccessContext.require().setRole("ADMIN"); AccessContext.require().setId(11L);
        GrowingRequests.Execution request = draft(); service.complete(3L, request);
        ArgumentCaptor<FarmExecutionEntity> capture = ArgumentCaptor.forClass(FarmExecutionEntity.class); verify(growing).insertExecution(capture.capture());
        FarmExecutionEntity saved = capture.getValue(); assertEquals(10L, saved.getPerformedBy()); assertEquals(11L, saved.getRecordedBy());
        when(growing.executionByRequest(request.getRequestKey())).thenReturn(saved); task.setStatus("COMPLETED");
        assertSame(saved, service.complete(3L, request)); verify(growing, times(1)).insertExecution(any()); verify(growing, times(1)).completeTask(3L);
    }
    @Test void anotherProductionPersonCannotCompleteSomeoneElsesTask() {
        AccessContext.require().setId(99L); assertThrows(AccessException.class, () -> service.complete(3L, draft())); verify(growing, never()).insertExecution(any());
    }
    @Test void futureOrPrePlantingExecutionIsRejected() {
        GrowingRequests.Execution request = draft(); request.setPerformedAt(LocalDateTime.now().plusHours(1)); assertThrows(IllegalArgumentException.class, () -> service.complete(3L, request));
        request.setPerformedAt(cycle.getPlantedOn().minusDays(1).atStartOfDay()); assertThrows(IllegalArgumentException.class, () -> service.complete(3L, request)); verify(growing, never()).insertExecution(any());
    }
    @Test void cancelledTasksCannotAcquireExecutionRecords() {
        task.setStatus("CANCELLED"); assertThrows(IllegalArgumentException.class, () -> service.complete(3L, draft())); verify(growing, never()).insertExecution(any());
    }
    @Test void endedCycleAndOtherOrganizationAreProtected() {
        cycle.setStatus("ENDED"); assertThrows(IllegalArgumentException.class, () -> service.complete(3L, draft()));
        AccessContext.require().setOrganizationId(99L); assertThrows(AccessException.class, () -> service.execution(3L));
    }
    @Test void quantityWithoutUnitCannotBeRecorded() {
        task.setTaskType("IRRIGATION"); GrowingRequests.Execution request = draft(); request.setUnit("");
        assertThrows(IllegalArgumentException.class, () -> service.complete(3L, request)); verify(growing, never()).insertExecution(any());
    }
}
