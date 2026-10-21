package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class WarehouseStockSummary {
    private Long batchCount;
    private BigDecimal weightKg;
}
