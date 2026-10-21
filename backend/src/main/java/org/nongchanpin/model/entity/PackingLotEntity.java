package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PackingLotEntity {
    private Long id;
    private Long gradeId;
    private String packingCode;
    private LocalDateTime packedAt;
    private Integer packageCount;
    private String packageUnit;
    private BigDecimal netWeightKg;
    private BigDecimal totalWeightKg;
    private String performerName;
    private String location;
    private String notes;
    private Long recordedBy;
    private String recorderName;
    private LocalDateTime recordedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestKey;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestHash;
    private Long harvestId;
    private String gradeName;
}
