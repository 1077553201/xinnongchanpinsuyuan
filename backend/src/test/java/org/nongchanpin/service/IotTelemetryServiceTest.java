package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.nongchanpin.config.IotProperties;
import org.nongchanpin.mapper.IotDeviceMapper;
import org.nongchanpin.mapper.IotGatewayMapper;
import org.nongchanpin.mapper.IotSensorReadingMapper;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.model.dto.TelemetryRequest;
import org.nongchanpin.model.entity.IotDataWindowEntity;
import org.nongchanpin.model.entity.IotDeviceEntity;
import org.nongchanpin.model.entity.IotGatewayEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.vo.IotIngestResult;

class IotTelemetryServiceTest {
    private final IotGatewayMapper gatewayMapper = mock(IotGatewayMapper.class);
    private final IotDeviceMapper deviceMapper = mock(IotDeviceMapper.class);
    private final IotSensorReadingMapper readingMapper = mock(IotSensorReadingMapper.class);
    private final ProductBatchMapper batchMapper = mock(ProductBatchMapper.class);
    private final IotAnomalyService anomalyService = mock(IotAnomalyService.class);
    private final IotWindowAggregationService aggregationService = mock(IotWindowAggregationService.class);
    private final IotProperties iotProperties = new IotProperties();

    @Test
    void ingestPersistsReadingNormalizesMetricAndTriggersWindow() {
        IotGatewayEntity gateway = new IotGatewayEntity();
        gateway.setId(5L);
        gateway.setGatewayCode("GW1");
        when(gatewayMapper.findByCode("GW1")).thenReturn(gateway);
        IotDeviceEntity device = new IotDeviceEntity();
        device.setId(6L);
        device.setDeviceCode("DEV1");
        when(deviceMapper.findByCode("DEV1")).thenReturn(device);
        ProductBatchEntity batch = new ProductBatchEntity();
        batch.setId(7L);
        batch.setBatchCode("B001");
        when(batchMapper.findByBatchCode("B001")).thenReturn(batch);
        when(anomalyService.evaluate(any(IotSensorReadingEntity.class))).thenReturn(Collections.emptyList());
        IotDataWindowEntity window = new IotDataWindowEntity();
        window.setId(8L);
        when(aggregationService.aggregateForReading(any(IotSensorReadingEntity.class))).thenReturn(window);

        IotIngestResult result = service().ingest(request("Temperature", "26.5", "B001"));

        assertEquals("temperature", result.getReading().getMetric());
        assertEquals(7L, result.getReading().getBatchId());
        assertEquals(8L, result.getWindow().getId());
        verify(readingMapper).insert(any(IotSensorReadingEntity.class));
        verify(deviceMapper).updateLastSeen(eq(6L), any(), eq("ACTIVE"));
        verify(aggregationService).aggregateForReading(any(IotSensorReadingEntity.class));
    }

    @Test
    void ingestWithoutBatchSkipsBatchLookup() {
        when(gatewayMapper.findByCode("GW1")).thenReturn(gateway());
        when(deviceMapper.findByCode("DEV1")).thenReturn(device());
        when(anomalyService.evaluate(any(IotSensorReadingEntity.class))).thenReturn(Collections.emptyList());

        IotIngestResult result = service().ingest(request("temperature", "26.5", null));

        assertEquals(null, result.getReading().getBatchId());
        verify(batchMapper, never()).findByBatchCode(any());
    }

    @Test
    void unknownGatewayRejectedWhenAutoRegisterDisabled() {
        iotProperties.setAutoRegisterGateway(false);
        when(gatewayMapper.findByCode("GW1")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service().ingest(request("temperature", "26.5", null)));
        verify(readingMapper, never()).insert(any(IotSensorReadingEntity.class));
    }

    @Test
    void missingValueRejected() {
        TelemetryRequest request = request("temperature", null, null);

        assertThrows(IllegalArgumentException.class, () -> service().ingest(request));
    }

    private IotTelemetryService service() {
        return new IotTelemetryService(gatewayMapper, deviceMapper, readingMapper, batchMapper, anomalyService,
                aggregationService, iotProperties);
    }

    private IotGatewayEntity gateway() {
        IotGatewayEntity gateway = new IotGatewayEntity();
        gateway.setId(5L);
        gateway.setGatewayCode("GW1");
        return gateway;
    }

    private IotDeviceEntity device() {
        IotDeviceEntity device = new IotDeviceEntity();
        device.setId(6L);
        device.setDeviceCode("DEV1");
        return device;
    }

    private TelemetryRequest request(String metric, String value, String batchCode) {
        TelemetryRequest request = new TelemetryRequest();
        request.setFarmId(1L);
        request.setGatewayCode("GW1");
        request.setDeviceCode("DEV1");
        request.setMetric(metric);
        request.setValue(value == null ? null : new BigDecimal(value));
        request.setUnit("celsius");
        request.setReportedAt(LocalDateTime.of(2026, 9, 29, 10, 3));
        request.setBatchCode(batchCode);
        return request;
    }
}
