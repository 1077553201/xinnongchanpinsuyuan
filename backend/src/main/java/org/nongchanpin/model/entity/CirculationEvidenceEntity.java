package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class CirculationEvidenceEntity {
    private Long id,itemId,eventId,txId,createdBy;
    private Integer version;
    private String manifestJson,manifestHash,previousHash,chainStatus,errorCode;
    private LocalDateTime createdAt;
    @JsonIgnore private String leaseToken;
    @JsonIgnore private LocalDateTime leaseUntil;
}
