package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class WarehouseUnitBalance {
    private String packageUnit;
    private Long packageCount;
    private BigDecimal weightKg;
}
