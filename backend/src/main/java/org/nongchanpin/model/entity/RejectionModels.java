package org.nongchanpin.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

public final class RejectionModels {
    private RejectionModels() {}
    @Data public static class Source {
        private Long organizationId,orderId,dispatchId,packingId,itemId,receiptEventId;
        private String traceCode,traceCodeHash,packingCode,productName,variety,packageUnit,receiptOutcome,sourceHash;
        private BigDecimal netWeightKg;
        private LocalDateTime receiptOccurredAt,receiptRecordedAt;
    }
    @Data public static class Case {
        private Long id,organizationId,dispatchId,packingId,itemId,warehouseId,recordedBy;
        private String sourceJson,sourceHash,initialHash,stateHash,status,packageUnit,quarantineLocation,reason,traceCode,packingCode,productName,variety,warehouseName;
        private Integer revision;
        private BigDecimal netWeightKg;
        private LocalDateTime lastPhysicalAt,recordedAt;
        @JsonIgnore private String requestKey,requestHash;
    }
    @Data public static class Event {
        private Long id,caseId,warehouseId,recordedBy;
        private Integer revision,targetRevision;
        private String kind,quarantineLocation,performerName,note,reference,previousHash,eventHash,payloadJson,warehouseName;
        private LocalDateTime occurredAt,recordedAt;
        @JsonIgnore private String requestKey,requestHash;
    }
    @Data public static class UnitBalance {
        private String packageUnit;
        private Long packageCount;
        private BigDecimal weightKg;
    }
}
