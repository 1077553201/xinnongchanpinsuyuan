package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class FarmExecutionEntity {
    private Long id;
    private Long taskId;
    private Long performedBy;
    private String performerName;
    private LocalDateTime performedAt;
    private String inputName;
    private BigDecimal quantity;
    private String unit;
    private String notes;
    private String evidenceReference;
    private Long recordedBy;
    private String recordedName;
    private LocalDateTime recordedAt;
    private String requestKey;
    @JsonIgnore private String requestHash;
}
