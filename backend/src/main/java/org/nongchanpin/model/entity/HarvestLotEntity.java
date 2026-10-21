package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class HarvestLotEntity {
    private Long id;
    private Long cycleId;
    private String lotCode;
    private LocalDateTime harvestedAt;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal weightKg;
    private String performerName;
    private String notes;
    private String status;
    private Long recordedBy;
    private String recorderName;
    private LocalDateTime recordedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestKey;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestHash;
    private BigDecimal allocatedKg;
    private BigDecimal lossKg;
    private BigDecimal packedKg;
}
