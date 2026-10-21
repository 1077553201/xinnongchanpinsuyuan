package org.nongchanpin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.config.IotProperties;
import org.nongchanpin.constants.IotConstants;
import org.nongchanpin.mapper.IotDeviceMapper;
import org.nongchanpin.mapper.IotGatewayMapper;
import org.nongchanpin.mapper.IotSensorReadingMapper;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.model.dto.TelemetryRequest;
import org.nongchanpin.model.entity.IotAlertEntity;
import org.nongchanpin.model.entity.IotDataWindowEntity;
import org.nongchanpin.model.entity.IotDeviceEntity;
import org.nongchanpin.model.entity.IotGatewayEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.vo.IotIngestResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class IotTelemetryService {
    private static final int VALUE_SCALE = 6;

    private final IotGatewayMapper gatewayMapper;
    private final IotDeviceMapper deviceMapper;
    private final IotSensorReadingMapper readingMapper;
    private final ProductBatchMapper batchMapper;
    private final IotAnomalyService anomalyService;
    private final IotWindowAggregationService windowAggregationService;
    private final IotProperties iotProperties;
    private final MonitoringService monitoringService;
    private final GrowingEnvironmentService growingEnvironment;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public IotIngestResult ingest(TelemetryRequest request) {
        return ingestInternal(request, false);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public IotIngestResult ingestMock(TelemetryRequest request) {
        return ingestInternal(request, true);
    }

    private IotIngestResult ingestInternal(TelemetryRequest request, boolean simulated) {
        validate(request);
        IotDeviceEntity device;
        if (simulated) {
            device = deviceMapper.findByCode(request.getDeviceCode().trim());
            if (device == null || gatewayMapper.findByCode(request.getGatewayCode().trim()) == null) {
                throw new IllegalArgumentException("模拟测试前请先登记网关和设备。");
            }
        } else {
            ensureGateway(request);
            device = ensureDevice(request);
        }
        Long batchId = resolveBatchId(request.getBatchCode());
        LocalDateTime reportedAt = request.getReportedAt() == null ? LocalDateTime.now() : request.getReportedAt();
        String metric = request.getMetric().trim().toLowerCase();

        IotSensorReadingEntity reading = new IotSensorReadingEntity();
        reading.setFarmId(request.getFarmId());
        reading.setGatewayCode(request.getGatewayCode().trim());
        reading.setDeviceCode(request.getDeviceCode().trim());
        reading.setBatchId(batchId);
        reading.setMetric(metric);
        reading.setValue(normalize(request.getValue()));
        reading.setUnit(request.getUnit());
        reading.setSignature(request.getSignature());
        reading.setReportedAt(reportedAt);
        reading.setStatus(simulated ? "SIMULATED" : IotConstants.READING_STATUS_RAW);
        if (request.getReportedAt() == null) reading.setResolutionReason("MISSING_COLLECTION_TIME");
        readingMapper.insert(reading);
        monitoringService.attribute(reading, device);
        if (reading.getCycleId() != null && reading.getBatchId() != null) {
            // 新种植周期尚无采收批次关系，不采信设备报文自行指定的旧批次归属。
            readingMapper.clearUnverifiedBatch(reading.getId());
            reading.setBatchId(null);
        }

        // 在线状态使用接收时间，迟到的历史样本不能把最近在线时间拨回过去。
        if (!simulated) deviceMapper.updateLastSeen(device.getId(), LocalDateTime.now(), IotConstants.DEVICE_STATUS_ACTIVE);

        boolean formal = !simulated && "BOUND".equals(reading.getResolution());
        if (formal) growingEnvironment.assess(reading);
        List<IotAlertEntity> alerts = formal ? anomalyService.evaluate(reading) : java.util.Collections.emptyList();
        IotDataWindowEntity window = formal ? windowAggregationService.aggregateForReading(reading) : null;

        IotIngestResult result = new IotIngestResult();
        result.setReading(reading);
        result.setAlerts(alerts);
        result.setWindow(window);
        return result;
    }

    private void validate(TelemetryRequest request) {
        if (request.getFarmId() == null) {
            throw new IllegalArgumentException("farmId is required");
        }
        if (!StringUtils.hasText(request.getGatewayCode())) {
            throw new IllegalArgumentException("gatewayCode is required");
        }
        if (!StringUtils.hasText(request.getDeviceCode())) {
            throw new IllegalArgumentException("deviceCode is required");
        }
        if (!StringUtils.hasText(request.getMetric())) {
            throw new IllegalArgumentException("metric is required");
        }
        if (request.getValue() == null) {
            throw new IllegalArgumentException("value is required");
        }
    }

    private IotGatewayEntity ensureGateway(TelemetryRequest request) {
        IotGatewayEntity gateway = gatewayMapper.findByCode(request.getGatewayCode().trim());
        if (gateway != null) {
            return gateway;
        }
        if (!iotProperties.isAutoRegisterGateway()) {
            throw new IllegalArgumentException("gateway not registered: " + request.getGatewayCode());
        }
        IotGatewayEntity created = new IotGatewayEntity();
        created.setFarmId(request.getFarmId());
        created.setGatewayCode(request.getGatewayCode().trim());
        created.setGatewayName(request.getGatewayCode().trim());
        created.setStatus(IotConstants.GATEWAY_STATUS_ACTIVE);
        gatewayMapper.insert(created);
        return gatewayMapper.findByCode(request.getGatewayCode().trim());
    }

    private IotDeviceEntity ensureDevice(TelemetryRequest request) {
        IotDeviceEntity device = deviceMapper.findByCode(request.getDeviceCode().trim());
        if (device != null) {
            return device;
        }
        if (!iotProperties.isAutoRegisterDevice()) {
            throw new IllegalArgumentException("device not registered: " + request.getDeviceCode());
        }
        IotGatewayEntity gateway = gatewayMapper.findByCode(request.getGatewayCode().trim());
        IotDeviceEntity created = new IotDeviceEntity();
        created.setGatewayId(gateway == null ? null : gateway.getId());
        created.setFarmId(request.getFarmId());
        created.setGatewayCode(request.getGatewayCode().trim());
        created.setDeviceCode(request.getDeviceCode().trim());
        created.setDeviceName(request.getDeviceCode().trim());
        created.setStatus(IotConstants.DEVICE_STATUS_ACTIVE);
        created.setLastSeenAt(LocalDateTime.now());
        deviceMapper.insert(created);
        return deviceMapper.findByCode(request.getDeviceCode().trim());
    }

    private Long resolveBatchId(String batchCode) {
        if (!StringUtils.hasText(batchCode)) {
            return null;
        }
        ProductBatchEntity batch = batchMapper.findByBatchCode(batchCode.trim());
        if (batch == null) {
            throw new IllegalArgumentException("batch not found: " + batchCode);
        }
        return batch.getId();
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(VALUE_SCALE, RoundingMode.HALF_UP);
    }
}
