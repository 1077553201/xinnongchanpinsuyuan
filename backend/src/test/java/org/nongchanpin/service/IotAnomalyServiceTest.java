package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nongchanpin.config.IotProperties;
import org.nongchanpin.constants.IotConstants;
import org.nongchanpin.mapper.IotAlertMapper;
import org.nongchanpin.model.entity.IotAlertEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;

class IotAnomalyServiceTest {
    @Test
    void valueAboveMaxCreatesHighAlert() {
        IotAlertMapper alertMapper = mock(IotAlertMapper.class);
        IotAnomalyService service = new IotAnomalyService(alertMapper,
                thresholds("temperature", "0", "40"));

        List<IotAlertEntity> alerts = service.evaluate(reading("temperature", "45.5"));

        assertEquals(1, alerts.size());
        assertEquals(IotConstants.ALERT_THRESHOLD_HIGH, alerts.get(0).getAlertType());
        assertEquals(IotConstants.LEVEL_WARN, alerts.get(0).getLevel());
        verify(alertMapper, times(1)).insert(any(IotAlertEntity.class));
    }

    @Test
    void valueBelowMinCreatesLowAlert() {
        IotAlertMapper alertMapper = mock(IotAlertMapper.class);
        IotAnomalyService service = new IotAnomalyService(alertMapper,
                thresholds("humidity", "20", "95"));

        List<IotAlertEntity> alerts = service.evaluate(reading("humidity", "12"));

        assertEquals(1, alerts.size());
        assertEquals(IotConstants.ALERT_THRESHOLD_LOW, alerts.get(0).getAlertType());
        verify(alertMapper, times(1)).insert(any(IotAlertEntity.class));
    }

    @Test
    void valueWithinRangeCreatesNoAlert() {
        IotAlertMapper alertMapper = mock(IotAlertMapper.class);
        IotAnomalyService service = new IotAnomalyService(alertMapper,
                thresholds("temperature", "0", "40"));

        List<IotAlertEntity> alerts = service.evaluate(reading("temperature", "25"));

        assertEquals(0, alerts.size());
        verify(alertMapper, times(0)).insert(any(IotAlertEntity.class));
    }

    @Test
    void metricWithoutThresholdIsIgnored() {
        IotAlertMapper alertMapper = mock(IotAlertMapper.class);
        IotAnomalyService service = new IotAnomalyService(alertMapper,
                thresholds("temperature", "0", "40"));

        List<IotAlertEntity> alerts = service.evaluate(reading("light", "99999"));

        assertEquals(0, alerts.size());
        verify(alertMapper, times(0)).insert(any(IotAlertEntity.class));
    }

    private IotProperties thresholds(String metric, String min, String max) {
        IotProperties properties = new IotProperties();
        IotProperties.AlertRange range = new IotProperties.AlertRange();
        range.setMin(new BigDecimal(min));
        range.setMax(new BigDecimal(max));
        properties.getAlerts().put(metric, range);
        return properties;
    }

    private IotSensorReadingEntity reading(String metric, String value) {
        IotSensorReadingEntity reading = new IotSensorReadingEntity();
        reading.setFarmId(1L);
        reading.setGatewayCode("GW1");
        reading.setDeviceCode("DEV1");
        reading.setMetric(metric);
        reading.setValue(new BigDecimal(value));
        reading.setReportedAt(LocalDateTime.of(2026, 9, 29, 10, 3));
        return reading;
    }
}
