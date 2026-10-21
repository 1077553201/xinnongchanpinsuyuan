package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class HarvestGradeEntity {
    private Long id;
    private Long harvestId;
    private String kind;
    private String gradeName;
    private BigDecimal weightKg;
    private LocalDateTime handledAt;
    private String performerName;
    private String location;
    private String reason;
    private Long recordedBy;
    private String recorderName;
    private LocalDateTime recordedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestKey;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestHash;
    private BigDecimal packedKg;
}
