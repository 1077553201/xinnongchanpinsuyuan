package org.nongchanpin.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.IotAlertMapper;
import org.nongchanpin.mapper.IotDataWindowMapper;
import org.nongchanpin.mapper.IotSensorReadingMapper;
import org.nongchanpin.model.entity.IotAlertEntity;
import org.nongchanpin.model.entity.IotDataWindowEntity;
import org.nongchanpin.model.entity.IotSensorReadingEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class IotQueryService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final IotSensorReadingMapper readingMapper;
    private final IotDataWindowMapper windowMapper;
    private final IotAlertMapper alertMapper;

    public List<IotSensorReadingEntity> listReadings(String deviceCode, String metric, Integer limit) {
        int size = normalizeLimit(limit);
        if (StringUtils.hasText(deviceCode) && StringUtils.hasText(metric)) {
            return readingMapper.findRecent(deviceCode, metric, size);
        }
        return readingMapper.findLatest(size);
    }

    public List<IotDataWindowEntity> listWindows(String deviceCode, Integer limit) {
        int size = normalizeLimit(limit);
        if (StringUtils.hasText(deviceCode)) {
            return windowMapper.findByDevice(deviceCode, size);
        }
        return windowMapper.findLatest(size);
    }

    public List<IotAlertEntity> listAlerts(String deviceCode, Integer limit) {
        int size = normalizeLimit(limit);
        if (StringUtils.hasText(deviceCode)) {
            return alertMapper.findByDevice(deviceCode, size);
        }
        return alertMapper.findLatest(size);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
