package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InventoryMovementEntity {
    private Long id;
    private Long packingId;
    private Long warehouseId;
    private String warehouseName;
    private String movementCode;
    private String kind;
    private Integer packageCount;
    private String packageUnit;
    private BigDecimal weightKg;
    private LocalDateTime occurredAt;
    private String performerName;
    private String destination;
    private String reason;
    private String reference;
    private Long recordedBy;
    private String recorderName;
    private LocalDateTime recordedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestKey;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestHash;
}
