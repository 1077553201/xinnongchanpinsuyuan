package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IotDataWindowEntity {
    private Long id;
    private Long farmId;
    private Long batchId;
    private String gatewayCode;
    private String deviceCode;
    private String metric;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private BigDecimal avgValue;
    private Integer sampleCount;
    private String dataHash;
    private String chainStatus;
    private String status;
}
