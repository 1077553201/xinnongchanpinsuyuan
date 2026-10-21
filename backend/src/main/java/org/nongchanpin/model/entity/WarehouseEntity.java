package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WarehouseEntity {
    private Long id;
    private Long organizationId;
    private String warehouseCode;
    private String warehouseName;
    private String location;
    private String notes;
    private Long createdBy;
    private LocalDateTime createdAt;
}
