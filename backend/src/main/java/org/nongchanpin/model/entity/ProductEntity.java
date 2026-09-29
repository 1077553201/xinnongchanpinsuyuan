package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProductEntity {
    private Long id;
    private Long organizationId;
    private String productCode;
    private String productName;
    private String category;
    private String variety;
    private String originPlace;
    private String description;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
