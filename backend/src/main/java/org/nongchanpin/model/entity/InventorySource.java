package org.nongchanpin.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class InventorySource extends PackingLotEntity {
    private Long organizationId;
    private Long cycleId;
    private String cycleCode;
    private String plotName;
    private String productName;
    private String variety;
    private String harvestCode;
    private Integer receivedCount;
    private Integer dispatchedCount;
    private Integer lossCount;
    private Integer availableCount;
}
