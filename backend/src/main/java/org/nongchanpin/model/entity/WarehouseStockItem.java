package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class WarehouseStockItem extends InventorySource {
    private BigDecimal availableWeightKg;
    private LocalDateTime lastMovementAt;
}
