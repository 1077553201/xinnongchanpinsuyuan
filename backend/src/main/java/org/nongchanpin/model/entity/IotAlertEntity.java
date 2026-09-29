package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IotAlertEntity {
    private Long id;
    private Long farmId;
    private String gatewayCode;
    private String deviceCode;
    private String metric;
    private String alertType;
    private String level;
    private BigDecimal value;
    private BigDecimal thresholdMin;
    private BigDecimal thresholdMax;
    private String message;
    private LocalDateTime occurredAt;
    private String status;
}
