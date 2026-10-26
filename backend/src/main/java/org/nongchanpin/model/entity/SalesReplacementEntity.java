package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class SalesReplacementEntity {
    private Long id,organizationId,originalOrderId,replacementOrderId,recordedBy;
    private String reason,impactJson,impactHash,recordHash;
    private LocalDateTime recordedAt;
    @JsonIgnore private String requestKey,requestHash;
}
