package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@Data
public class SalesOrderEntity {
    private Long id;
    private Long organizationId;
    private String orderCode;
    private LocalDate orderedOn;
    private String customerName;
    private String customerContact;
    private String deliveryAddress;
    private String notes;
    @JsonSerialize(using=ToStringSerializer.class) private BigDecimal totalAmount;
    private String status;
    private Long recordedBy;
    private String recorderName;
    private LocalDateTime recordedAt;
    private Long closedBy;
    private LocalDateTime closedAt;
    private String closeReason;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestKey;
    @com.fasterxml.jackson.annotation.JsonIgnore private String requestHash;
}
