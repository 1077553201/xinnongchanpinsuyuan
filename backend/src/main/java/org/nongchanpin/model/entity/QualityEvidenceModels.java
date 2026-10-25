package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

public final class QualityEvidenceModels {
    private QualityEvidenceModels() {}
    @Data public static class Revision {
        private Long previousReportId,replacementReportId,recordedBy;
        private String reason,previousReportNo,replacementReportNo,previousHash,replacementHash;
        private LocalDateTime recordedAt;
    }
    @Data public static class Evidence {
        private Long id,reportId,itemId,eventId,txId,createdBy;
        private Integer version;
        private String manifestJson,manifestHash,previousHash,chainStatus,errorCode;
        private LocalDateTime createdAt;
        @JsonIgnore private String leaseToken;
        @JsonIgnore private LocalDateTime leaseUntil;
    }
}
