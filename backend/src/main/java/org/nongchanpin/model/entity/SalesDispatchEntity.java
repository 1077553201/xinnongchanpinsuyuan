package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SalesDispatchEntity {
    private Long id;
    private Long lineId;
    private Long movementId;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestKey;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestHash;
    private String movementCode;
    private Integer packageCount;
    private String packageUnit;
    private BigDecimal weightKg;
    private LocalDateTime occurredAt;
    private String performerName;
    private String recorderName;
    private LocalDateTime recordedAt;
    private String reason;
    private String reference;
}
