package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@Data
public class SalesLineEntity {
    private Long id;
    private Long orderId;
    private Long packingId;
    private Long warehouseId;
    private String packingCode;
    private String warehouseName;
    private String productName;
    private String variety;
    private String packageUnit;
    private BigDecimal netWeightKg;
    private Integer packageCount;
    @JsonSerialize(using=ToStringSerializer.class) private BigDecimal unitPrice;
    @JsonSerialize(using=ToStringSerializer.class) private BigDecimal lineAmount;
    private Integer dispatchedCount;
    private Long cycleId;
}
