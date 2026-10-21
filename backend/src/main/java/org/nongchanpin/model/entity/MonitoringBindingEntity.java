package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MonitoringBindingEntity {
    private Long id;
    private Long deviceId;
    private Long plotId;
    private Long cycleId;
    private String deviceCode;
    private String deviceName;
    private String cycleCode;
    private String plotName;
    private String gatewayCode;
    private Long farmId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long createdBy;
    private Long endedBy;
    private String endReason;
}
