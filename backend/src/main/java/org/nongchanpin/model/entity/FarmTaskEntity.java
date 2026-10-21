package org.nongchanpin.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FarmTaskEntity {
    private Long id;
    private Long cycleId;
    private String taskCode;
    private String taskType;
    private String title;
    private LocalDate plannedOn;
    private Long assignedTo;
    private String assigneeName;
    private String instructions;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancelReason;
}
