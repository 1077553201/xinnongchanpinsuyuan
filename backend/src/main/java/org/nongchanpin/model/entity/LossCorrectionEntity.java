package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class LossCorrectionEntity {
    private Long id,movementId,recordedBy,balanceBefore,balanceAfter;
    private Integer revision,previousCount,correctedCount;
    private LocalDateTime verifiedAt,recordedAt;
    private String performerName,reason,reference,originalHash,previousHash,impactJson,impactHash,payloadJson,correctionHash;
    @JsonIgnore private String requestKey,requestHash;
}
