package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IotDeviceEntity {
    private Long id;
    private Long organizationId;
    private Long gatewayId;
    private Long farmId;
    private String gatewayCode;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String status;
    private LocalDateTime lastSeenAt;
}
