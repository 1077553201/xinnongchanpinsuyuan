package org.nongchanpin.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class TelemetryRequest {
    private Long farmId;
    private String gatewayCode;
    private String deviceCode;
    private String metric;
    private BigDecimal value;
    private String unit;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportedAt;
    private String signature;
    private String batchCode;
}
