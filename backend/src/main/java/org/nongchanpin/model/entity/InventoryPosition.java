package org.nongchanpin.model.entity;

import lombok.Data;

@Data
public class InventoryPosition {
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String location;
    private Integer receivedCount;
    private Integer dispatchedCount;
    private Integer lossCount;
    private Integer availableCount;
}
