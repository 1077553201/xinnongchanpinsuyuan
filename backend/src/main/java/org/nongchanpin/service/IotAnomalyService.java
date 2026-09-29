package org.nongchanpin.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.config.IotProperties;
import org.nongchanpin.constants.IotConstants;
import org.nongchanpin.mapper.IotAlertMapper;
import org.nongchanpin.model.entity.IotAlertEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IotAnomalyService {
    private final IotAlertMapper alertMapper;
    private final IotProperties iotProperties;

    public List<IotAlertEntity> evaluate(IotSensorReadingEntity reading) {
        if (reading.getValue() == null) {
            return Collections.emptyList();
        }
        IotProperties.AlertRange range = iotProperties.getAlerts().get(reading.getMetric());
        if (range == null) {
            return Collections.emptyList();
        }
        List<IotAlertEntity> alerts = new ArrayList<>();
        if (range.getMax() != null && reading.getValue().compareTo(range.getMax()) > 0) {
            alerts.add(build(reading, range, IotConstants.ALERT_THRESHOLD_HIGH, range.getMax()));
        } else if (range.getMin() != null && reading.getValue().compareTo(range.getMin()) < 0) {
            alerts.add(build(reading, range, IotConstants.ALERT_THRESHOLD_LOW, range.getMin()));
        }
        for (IotAlertEntity alert : alerts) {
            alertMapper.insert(alert);
        }
        return alerts;
    }

    private IotAlertEntity build(IotSensorReadingEntity reading, IotProperties.AlertRange range, String alertType,
            BigDecimal threshold) {
        IotAlertEntity alert = new IotAlertEntity();
        alert.setFarmId(reading.getFarmId());
        alert.setGatewayCode(reading.getGatewayCode());
        alert.setDeviceCode(reading.getDeviceCode());
        alert.setMetric(reading.getMetric());
        alert.setAlertType(alertType);
        alert.setLevel(IotConstants.LEVEL_WARN);
        alert.setValue(reading.getValue());
        alert.setThresholdMin(range.getMin());
        alert.setThresholdMax(range.getMax());
        alert.setOccurredAt(reading.getReportedAt());
        alert.setStatus(IotConstants.ALERT_STATUS_OPEN);
        alert.setMessage(buildMessage(reading, alertType, threshold));
        return alert;
    }

    private String buildMessage(IotSensorReadingEntity reading, String alertType, BigDecimal threshold) {
        String direction = IotConstants.ALERT_THRESHOLD_HIGH.equals(alertType) ? "above max" : "below min";
        return reading.getMetric() + " " + reading.getValue().toPlainString() + " " + direction + " "
                + threshold.toPlainString() + " on device " + reading.getDeviceCode();
    }
}
