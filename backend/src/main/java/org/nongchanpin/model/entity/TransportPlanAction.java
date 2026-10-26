package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class TransportPlanAction {
    private Long id,shipmentId,recordedBy;
    private Integer revision,packageCount;
    private String action,reason,shipmentCode,carrier,trackingReference,driverName,driverContact,notes,packageUnit,impactJson,impactHash,previousHash,actionHash;
    private LocalDateTime correctionRecordedAt,recordedAt;
    @JsonIgnore private String requestKey,requestHash;
}
