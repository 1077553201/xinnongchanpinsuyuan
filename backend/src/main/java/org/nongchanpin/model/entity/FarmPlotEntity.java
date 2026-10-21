package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FarmPlotEntity {
    private Long id;
    private Long organizationId;
    private String plotCode;
    private String plotName;
    private String location;
    private BigDecimal areaMu;
    private String managerName;
    private String notes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
