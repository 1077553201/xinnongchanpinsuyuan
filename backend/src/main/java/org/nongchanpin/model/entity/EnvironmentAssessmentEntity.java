package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EnvironmentAssessmentEntity {
    private Long readingId;
    private Long cycleId;
    private Long ruleId;
    private String metric;
    private String result;
    private String reason;
    private BigDecimal value;
    private String unit;
    private String deviceCode;
    private LocalDateTime reportedAt;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String ruleUnit;
    private Long caseId;
    private String caseStatus;
    private String actionTaken;
    private String observation;
    private LocalDateTime handledAt;
    private Long handledBy;
    private String handlerName;
}
