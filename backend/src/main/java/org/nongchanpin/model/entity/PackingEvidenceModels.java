package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

public final class PackingEvidenceModels {
    private PackingEvidenceModels() {}
    @Data public static class Snapshot {
        private Long packingId,createdBy;
        private String manifestJson,manifestHash,batchPayload;
        private LocalDateTime createdAt;
        @JsonIgnore private String leaseToken;
        @JsonIgnore private LocalDateTime leaseUntil;
    }
    @Data public static class Job {
        private Long id,packingId,businessId,txId;
        private String businessType,payloadJson,payloadHash,chainStatus,errorCode;
        private LocalDateTime updatedAt;
    }
}
