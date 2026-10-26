package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.security.*;

class MonitoringServiceTest {
    private final MonitoringMapper mapper = mock(MonitoringMapper.class);
    private final ProductionMapper production = mock(ProductionMapper.class);
    private final MonitoringService service = new MonitoringService(mapper, production, mock(AuditService.class));
    private final IotDeviceEntity device = new IotDeviceEntity();
    private final MonitoringBindingEntity binding = new MonitoringBindingEntity();
    @BeforeEach void setup() {
        Actor actor = new Actor(); actor.setId(1L); actor.setRole("MANAGER"); actor.setOrganizationId(10L); AccessContext.set(actor);
        device.setId(8L); device.setOrganizationId(10L); device.setFarmId(1L); device.setGatewayCode("GW");
        when(mapper.lockDevice(8L)).thenReturn(device);
        binding.setId(9L); binding.setCycleId(2L); binding.setPlotId(3L); binding.setFarmId(1L); binding.setGatewayCode("GW");
        PlantingCycleEntity cycle = new PlantingCycleEntity(); cycle.setId(2L); cycle.setPlotId(3L); cycle.setStatus("ACTIVE");
        when(production.cycle(2L)).thenReturn(cycle); when(production.lockCycle(2L)).thenReturn(cycle);
        FarmPlotEntity plot = new FarmPlotEntity(); plot.setId(3L); plot.setOrganizationId(10L);
        when(production.lockPlot(3L)).thenReturn(plot); when(production.plot(3L)).thenReturn(plot);
        when(production.activeOrganization(10L)).thenReturn(1);
    }
    @AfterEach void cleanup() { AccessContext.clear(); }
    @Test void disabledOrganizationCannotReceiveNewBinding() {
        when(production.activeOrganization(10L)).thenReturn(0);
        MonitoringService.Bind request = new MonitoringService.Bind(); request.setDeviceId(8L); request.setCycleId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.bind(request));
        verify(mapper, never()).insertBinding(any());
    }
    private IotSensorReadingEntity reading() {
        IotSensorReadingEntity reading = new IotSensorReadingEntity(); reading.setId(11L); reading.setFarmId(1L);
        reading.setGatewayCode("GW"); reading.setReportedAt(LocalDateTime.now().minusMinutes(5)); return reading;
    }
    @Test void lateReadingUsesHistoryRatherThanCurrentCycle() {
        IotSensorReadingEntity reading = reading();
        when(mapper.atTime(8L, reading.getReportedAt())).thenReturn(List.of(binding));
        service.attribute(reading, device);
        assertEquals(2L, reading.getCycleId()); assertEquals(9L, reading.getBindingId()); assertEquals("BOUND", reading.getResolution());
        verify(mapper).attribute(reading);
    }
    @Test void ambiguousUnboundAndMismatchedSourcesRemainUnresolved() {
        IotSensorReadingEntity reading = reading();
        when(mapper.atTime(8L, reading.getReportedAt())).thenReturn(List.of());
        service.attribute(reading, device); assertNull(reading.getCycleId());
        when(mapper.atTime(8L, reading.getReportedAt())).thenReturn(List.of(binding, binding));
        service.attribute(reading, device); assertEquals("AMBIGUOUS_BINDING_HISTORY", reading.getResolutionReason());
        when(mapper.atTime(8L, reading.getReportedAt())).thenReturn(List.of(binding)); reading.setFarmId(99L);
        service.attribute(reading, device); assertNull(reading.getCycleId()); assertEquals("GATEWAY_OR_FARM_MISMATCH", reading.getResolutionReason());
    }
    @Test void futureReadingCannotBeAssignedToCurrentBinding() {
        IotSensorReadingEntity reading = reading(); reading.setReportedAt(LocalDateTime.now().plusHours(1));
        service.attribute(reading, device); assertNull(reading.getCycleId()); assertEquals("FUTURE_COLLECTION_TIME", reading.getResolutionReason());
        verify(mapper, never()).atTime(any(), any());
    }
    @Test void missingCollectionTimeCannotBeReplacedWithReceiptTimeForAttribution() {
        IotSensorReadingEntity reading = reading(); reading.setResolutionReason("MISSING_COLLECTION_TIME");
        service.attribute(reading, device); assertNull(reading.getCycleId());
        assertEquals("MISSING_COLLECTION_TIME", reading.getResolutionReason()); verify(mapper, never()).atTime(any(), any());
    }
    @Test void rejectsOtherOrganizationAndAlreadyBoundDevice() {
        MonitoringService.Bind request = new MonitoringService.Bind(); request.setDeviceId(8L); request.setCycleId(2L);
        device.setOrganizationId(20L); assertThrows(IllegalArgumentException.class, () -> service.bind(request));
        device.setOrganizationId(10L); when(mapper.activeBindings(8L)).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.bind(request)); verify(mapper, never()).insertBinding(any());
    }
    @Test void sharedServiceRejectsViewerAndCrossOrganizationRead() {
        AccessContext.require().setRole("VIEWER");
        assertThrows(AccessException.class, () -> service.bind(new MonitoringService.Bind()));
        AccessContext.require().setOrganizationId(20L);
        assertThrows(AccessException.class, () -> service.readings(2L));
    }
    @Test void mobileVehicleCannotMasqueradeAsFixedSensor() {
        device.setDeviceType("ROBOT_CAR");
        MonitoringService.Bind request = new MonitoringService.Bind(); request.setDeviceId(8L); request.setCycleId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.bind(request)); verify(mapper, never()).insertBinding(any());
    }
}
