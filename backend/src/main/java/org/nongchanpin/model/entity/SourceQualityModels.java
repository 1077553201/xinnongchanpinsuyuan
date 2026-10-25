package org.nongchanpin.model.entity;

import java.time.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

public final class SourceQualityModels {
    private SourceQualityModels() {}
    @Data public static class Attachment { private byte[] content; }
    @Data public static class Source {
        private Long organizationId,harvestId,packingId;
        private String harvestCode,packingCode,cycleCode,productName,variety;
        private LocalDateTime harvestedAt,packedAt;
    }
    @Data public static class Report {
        private Long id,organizationId,harvestId,packingId,recordedBy,publishedBy,withdrawnBy;
        private String scopeType,reportNo,agencyName,declaredResult,fileName,fileType,fileSha256,manifestJson,manifestHash,status;
        private Integer fileSize;
        private boolean attachmentPublic;
        private LocalDate sampledOn,checkedOn;
        private LocalDateTime recordedAt,publishedAt,withdrawnAt;
        private String recorderName,publisherName,withdrawReason;
        @JsonIgnore private String requestKey,requestHash;
    }
}
