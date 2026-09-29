package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IotSensorReadingEntity {
    private Long id;
    private Long farmId;
    private String gatewayCode;
    private String deviceCode;
    private Long batchId;
    private String metric;
    private BigDecimal value;
    private String unit;
    private String signature;
    private LocalDateTime reportedAt;
    private String status;
}
