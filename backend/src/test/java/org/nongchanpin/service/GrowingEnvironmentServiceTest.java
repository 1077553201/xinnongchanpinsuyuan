package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.nongchanpin.mapper.GrowingMapper;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.dto.GrowingRequests;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;

class GrowingEnvironmentServiceTest {
    private final GrowingMapper growing = mock(GrowingMapper.class);
    private final ProductionMapper production = mock(ProductionMapper.class);
    private final GrowingEnvironmentService service = new GrowingEnvironmentService(growing, new GrowingAccess(production), mock(AuditService.class), new PayloadService());
    private final IotSensorReadingEntity reading = new IotSensorReadingEntity();
    private final GrowingRuleEntity rule = new GrowingRuleEntity();

    @BeforeEach void setup() {
        Actor actor = new Actor(); actor.setId(10L); actor.setOrganizationId(20L); actor.setRole("MANAGER"); AccessContext.set(actor);
        PlantingCycleEntity cycle = new PlantingCycleEntity(); cycle.setId(1L); cycle.setPlotId(2L); cycle.setStatus("ACTIVE");
        when(production.cycle(1L)).thenReturn(cycle); when(production.lockCycle(1L)).thenReturn(cycle);
        FarmPlotEntity plot = new FarmPlotEntity(); plot.setId(2L); plot.setOrganizationId(20L); when(production.plot(2L)).thenReturn(plot);
        reading.setId(3L); reading.setCycleId(1L); reading.setMetric("temperature"); reading.setReportedAt(LocalDateTime.now().minusMinutes(1));
        reading.setStatus("RAW"); reading.setResolution("BOUND"); reading.setValue(new BigDecimal("30")); reading.setUnit("°C");
        rule.setId(4L); rule.setCycleId(1L); rule.setMetric("temperature"); rule.setMinValue(new BigDecimal("10")); rule.setMaxValue(new BigDecimal("25")); rule.setUnit("celsius");
        when(growing.rulesAt(1L, "temperature", reading.getReportedAt())).thenReturn(List.of(rule));
    }
    @AfterEach void clear() { AccessContext.clear(); }
    private EnvironmentAssessmentEntity result() {
        ArgumentCaptor<EnvironmentAssessmentEntity> capture = ArgumentCaptor.forClass(EnvironmentAssessmentEntity.class);
        verify(growing).insertAssessment(capture.capture()); return capture.getValue();
    }
    private GrowingRequests.Rule draft() {
        GrowingRequests.Rule request = new GrowingRequests.Rule(); request.setMetric("temperature"); request.setMinValue(new BigDecimal("10")); request.setMaxValue(new BigDecimal("25"));
        request.setExpectedIntervalMinutes(5); request.setBasis("联调测试依据，不是农艺推荐"); request.setRequestKey("00000000-0000-4000-8000-000000000001"); return request;
    }
    @Test void comparesUsingRuleAtCollectionTimeAndCreatesExceededCase() {
        service.assess(reading); assertEquals("ABOVE", result().getResult()); assertEquals(4L, resultRuleId());
        verify(growing).rulesAt(1L, "temperature", reading.getReportedAt()); verify(growing).insertCase(any());
    }
    private Long resultRuleId() { ArgumentCaptor<EnvironmentAssessmentEntity> capture = ArgumentCaptor.forClass(EnvironmentAssessmentEntity.class); verify(growing).insertAssessment(capture.capture()); return capture.getValue().getRuleId(); }
    @Test void exactBoundaryIsWithinRangeAndDoesNotCreateCase() {
        reading.setValue(new BigDecimal("25")); service.assess(reading); assertEquals("IN_RANGE", result().getResult()); verify(growing, never()).insertCase(any());
    }
    @Test void missingRuleNeverMeansQualified() {
        when(growing.rulesAt(any(), any(), any())).thenReturn(List.of()); service.assess(reading);
        assertEquals("NO_RULE", result().getResult()); verify(growing, never()).insertCase(any());
    }
    @Test void mismatchedUnitDoesNotConvertOrClaimWithinRange() {
        reading.setUnit("fahrenheit"); service.assess(reading); assertEquals("UNIT_MISMATCH", result().getResult()); verify(growing, never()).insertCase(any());
    }
    @Test void simulatedAndUnresolvedSamplesNeverEnterAssessments() {
        reading.setStatus("SIMULATED"); service.assess(reading); reading.setStatus("RAW"); reading.setResolution("UNRESOLVED"); service.assess(reading); verifyNoInteractions(growing);
    }
    @Test void invalidRangeIsRejectedBeforeRetiringPreviousVersion() {
        GrowingRequests.Rule request = draft(); request.setMaxValue(new BigDecimal("5"));
        assertThrows(IllegalArgumentException.class, () -> service.configure(1L, request)); verify(growing, never()).retireMetric(any(), any(), any());
    }
    @Test void sameRequestCanBeRetriedWithoutCreatingAnotherVersion() {
        GrowingRequests.Rule request = draft();
        service.configure(1L, request);
        ArgumentCaptor<GrowingRuleEntity> capture = ArgumentCaptor.forClass(GrowingRuleEntity.class); verify(growing).insertRule(capture.capture());
        GrowingRuleEntity saved = capture.getValue(); when(growing.ruleByRequest(request.getRequestKey())).thenReturn(saved);
        assertSame(saved, service.configure(1L, request)); verify(growing, times(1)).insertRule(any());
        request.setBasis("不同内容"); assertThrows(IllegalArgumentException.class, () -> service.configure(1L, request));
    }
    @Test void readOnlyAndOtherOrganizationCannotConfigureOrRead() {
        AccessContext.require().setRole("VIEWER"); assertThrows(AccessException.class, () -> service.configure(1L, draft()));
        AccessContext.require().setOrganizationId(99L); assertThrows(AccessException.class, () -> service.overview(1L)); verifyNoInteractions(growing);
    }
    @Test void absenceOfSamplesIsReportedAsMissingNotWithinRange() {
        rule.setExpectedIntervalMinutes(1); rule.setEffectiveFrom(LocalDateTime.now().minusMinutes(10));
        when(growing.rules(1L)).thenReturn(List.of(rule)); when(growing.assessments(1L)).thenReturn(List.of());
        List<?> coverage = (List<?>) service.overview(1L).get("coverage");
        assertEquals("NO_DATA", ((java.util.Map<?, ?>) coverage.get(0)).get("status"));
    }
    @Test void handlingRecordsActionWithoutClaimingEnvironmentalRecovery() {
        EnvironmentAssessmentEntity item = new EnvironmentAssessmentEntity(); item.setCaseId(7L); item.setCycleId(1L); item.setCaseStatus("OPEN");
        when(growing.caseById(7L)).thenReturn(item); when(growing.handleCase(any(), any(), any(), any(), any())).thenReturn(1);
        GrowingRequests.HandleCase request = new GrowingRequests.HandleCase(); request.setActionTaken("通风并安排复核"); request.setObservation("等待后续采集");
        service.handle(7L, request); verify(growing).handleCase(eq(7L), eq(10L), any(), eq("通风并安排复核"), eq("等待后续采集"));
    }
}
