package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class GrowingRuleEntity {
    private Long id;
    private Long cycleId;
    private String metric;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private Integer expectedIntervalMinutes;
    private String basis;
    private String notes;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long createdBy;
    private String requestKey;
    @JsonIgnore private String requestHash;
}
