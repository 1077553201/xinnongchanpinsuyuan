package org.nongchanpin.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PlantingCycleEntity {
    private Long id;
    private Long plotId;
    private Long productId;
    private String cycleCode;
    private String productName;
    private String variety;
    private LocalDate plantedOn;
    private LocalDate expectedHarvestOn;
    private String managerName;
    private String environmentNotes;
    private String status;
    private LocalDate endedOn;
    private String endReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
