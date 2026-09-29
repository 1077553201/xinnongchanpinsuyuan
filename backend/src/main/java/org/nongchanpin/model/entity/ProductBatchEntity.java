package org.nongchanpin.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProductBatchEntity {
    private Long id;
    private Long productId;
    private Long organizationId;
    private String batchCode;
    private String productionArea;
    private LocalDate plantDate;
    private LocalDate harvestDate;
    private String batchMetadata;
    private String chainStatus;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
