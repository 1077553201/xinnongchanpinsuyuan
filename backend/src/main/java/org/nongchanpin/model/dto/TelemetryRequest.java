package org.nongchanpin.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TelemetryRequest {
    private Long farmId;
    private String gatewayCode;
    private String deviceCode;
    private String metric;
    private BigDecimal value;
    private String unit;
    private LocalDateTime reportedAt;
    private String signature;
    private String batchCode;
}
