package org.nongchanpin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.chain.ChainStatus;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.config.IotProperties;
import org.nongchanpin.constants.IotConstants;
import org.nongchanpin.mapper.IotDataWindowMapper;
import org.nongchanpin.mapper.IotSensorReadingMapper;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.MonitoringMapper;
import org.nongchanpin.model.command.AppendSensorWindowCommand;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.IotDataWindowEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IotWindowAggregationService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final int VALUE_SCALE = 6;
    private static final String WINDOW_STATUS_ACTIVE = "ACTIVE";

    private final IotSensorReadingMapper readingMapper;
    private final IotDataWindowMapper windowMapper;
    private final ProductBatchMapper batchMapper;
    private final ChainService chainService;
    private final ChainTransactionService chainTransactionService;
    private final ChainWriteSupport chainWriteSupport;
    private final PayloadService payloadService;
    private final IotProperties iotProperties;
    private final ContractConfig contractConfig;
    private final MonitoringMapper monitoringMapper;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public IotDataWindowEntity aggregateForReading(IotSensorReadingEntity reading) {
        LocalDateTime start = bucketStart(reading.getReportedAt());
        LocalDateTime end = start.plusMinutes(iotProperties.getWindowMinutes());
        return aggregateScoped(reading.getDeviceCode(), reading.getMetric(), reading.getFarmId(),
                reading.getGatewayCode(), start, end, reading.getBatchId(), reading.getCycleId(), reading.getBindingId(), true);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public IotDataWindowEntity aggregate(String deviceCode, String metric, Long farmId, String gatewayCode,
            LocalDateTime start, LocalDateTime end) {
        monitoringMapper.lockDeviceForAggregation(deviceCode);
        return aggregateScoped(deviceCode, metric, farmId, gatewayCode, start, end, null, null, null, false);
    }

    private IotDataWindowEntity aggregateScoped(String deviceCode, String metric, Long farmId, String gatewayCode,
            LocalDateTime start, LocalDateTime end, Long targetBatchId, Long targetCycleId, Long targetBindingId, boolean scoped) {
        if (start == null || end == null || !end.isAfter(start)) throw new IllegalArgumentException("invalid aggregation window");
        List<IotSensorReadingEntity> readings = readingMapper.findInWindow(deviceCode, metric, start, end);
        final Long requestedFarmId = farmId;
        final String requestedGateway = gatewayCode;
        if (scoped) readings = readings.stream().filter(reading -> Objects.equals(targetBatchId, reading.getBatchId())
                && Objects.equals(targetCycleId, reading.getCycleId()) && Objects.equals(targetBindingId, reading.getBindingId())
                && Objects.equals(requestedFarmId, reading.getFarmId()) && Objects.equals(requestedGateway, reading.getGatewayCode())).collect(Collectors.toList());
        else if (readings.stream().map(reading -> java.util.Arrays.asList(reading.getBatchId(), reading.getCycleId(),
                reading.getBindingId(), reading.getFarmId(), reading.getGatewayCode())).distinct().count() > 1) {
            throw new IllegalArgumentException("window includes multiple sources; aggregate by planting binding and batch");
        }
        if (readings.isEmpty()) {
            return null;
        }
        if ((farmId != null && !farmId.equals(readings.get(0).getFarmId()))
                || (gatewayCode != null && !gatewayCode.isBlank() && !gatewayCode.equals(readings.get(0).getGatewayCode()))) {
            throw new IllegalArgumentException("aggregation context does not match collected readings");
        }
        if (farmId == null) farmId = readings.get(0).getFarmId();
        if (gatewayCode == null || gatewayCode.isBlank()) gatewayCode = readings.get(0).getGatewayCode();
        Long cycleId = readings.get(0).getCycleId();
        Long plotId = readings.get(0).getPlotId();
        Long bindingId = readings.get(0).getBindingId();
        BigDecimal min = null;
        BigDecimal max = null;
        BigDecimal sum = BigDecimal.ZERO;
        int sampleCount = 0;
        Long batchId = null;
        for (IotSensorReadingEntity reading : readings) {
            BigDecimal value = reading.getValue();
            if (value == null) {
                continue;
            }
            min = min == null || value.compareTo(min) < 0 ? value : min;
            max = max == null || value.compareTo(max) > 0 ? value : max;
            sum = sum.add(value);
            sampleCount++;
            if (reading.getBatchId() != null) {
                batchId = reading.getBatchId();
            }
        }
        if (sampleCount == 0) {
            return null;
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(sampleCount), VALUE_SCALE, RoundingMode.HALF_UP);
        Map<String, Object> payload = payloadService.map();
        payload.put("deviceCode", deviceCode);
        payload.put("batchId", batchId);
        payload.put("farmId", farmId);
        payload.put("gatewayCode", gatewayCode);
        payload.put("metric", metric);
        payload.put("windowStart", start.toString());
        payload.put("windowEnd", end.toString());
        payload.put("minValue", normalize(min));
        payload.put("maxValue", normalize(max));
        payload.put("avgValue", normalize(avg));
        payload.put("sampleCount", sampleCount);
        if (cycleId != null) {
            payload.put("cycleId", cycleId);
            payload.put("plotId", plotId);
            payload.put("bindingId", bindingId);
        }
        String dataHash = payloadService.requestHash(payload);

        IotDataWindowEntity existing = windowMapper.findByDataHash(dataHash);
        if (existing != null) {
            return existing;
        }

        IotDataWindowEntity window = new IotDataWindowEntity();
        window.setFarmId(farmId);
        window.setCycleId(cycleId);
        window.setPlotId(plotId);
        window.setBindingId(bindingId);
        window.setBatchId(batchId);
        window.setGatewayCode(gatewayCode);
        window.setDeviceCode(deviceCode);
        window.setMetric(metric);
        window.setWindowStart(start);
        window.setWindowEnd(end);
        window.setMinValue(min);
        window.setMaxValue(max);
        window.setAvgValue(avg);
        window.setSampleCount(sampleCount);
        window.setDataHash(dataHash);
        window.setChainStatus(ChainStatus.PENDING.name());
        window.setStatus(WINDOW_STATUS_ACTIVE);
        windowMapper.insert(window);
        if (cycleId != null) monitoringMapper.attributeWindow(window);

        String chainStatus = anchor(window, payload, start, end);
        windowMapper.updateChainStatus(window.getId(), chainStatus);
        return windowMapper.findByDataHash(dataHash);
    }

    private String anchor(IotDataWindowEntity window, Map<String, Object> payload, LocalDateTime start,
            LocalDateTime end) {
        if (window.getBatchId() == null) {
            return ChainStatus.CHAIN_DISABLED.name();
        }
        ProductBatchEntity batch = batchMapper.findById(window.getBatchId());
        if (batch == null) {
            return ChainStatus.CHAIN_DISABLED.name();
        }
        String windowId = window.getDeviceCode() + ":" + window.getMetric() + ":"
                + start.atZone(ZONE_ID).toEpochSecond() + ":" + window.getDataHash();
        ChainTransactionEntity tx = chainTransactionService.createPending(IotConstants.WINDOW_CHAIN_BUSINESS_TYPE,
                window.getId(), HashUtils.hashBusinessIdHex(windowId), contractConfig.getAgriTraceAddress(),
                IotConstants.WINDOW_CHAIN_EVENT_NAME, "0x" + window.getDataHash(), window.getDataHash());

        AppendSensorWindowCommand command = new AppendSensorWindowCommand();
        command.setBatchCode(batch.getBatchCode());
        command.setWindowId(windowId);
        command.setDeviceCode(window.getDeviceCode());
        command.setPayload(payload);
        command.setWindowStart(start.atZone(ZONE_ID).toEpochSecond());
        command.setWindowEnd(end.atZone(ZONE_ID).toEpochSecond());
        return chainWriteSupport.submit(tx, () -> chainService.appendSensorWindow(command));
    }

    private LocalDateTime bucketStart(LocalDateTime reportedAt) {
        int windowMinutes = Math.max(iotProperties.getWindowMinutes(), 1);
        int bucketMinute = (reportedAt.getMinute() / windowMinutes) * windowMinutes;
        return reportedAt.truncatedTo(ChronoUnit.HOURS).withMinute(bucketMinute).withSecond(0).withNano(0);
    }

    private String normalize(BigDecimal value) {
        return value.setScale(VALUE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
