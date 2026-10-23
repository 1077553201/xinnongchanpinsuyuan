package org.nongchanpin.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

public final class PackingTraceModels {
    private PackingTraceModels() {}
    @Data @EqualsAndHashCode(callSuper=true)
    public static class Source extends PackingLotEntity {
        private Long cycleId,plotId,productId,organizationId;
        private String cycleCode,plotCode,plotName,productName,variety,harvestCode;
        private LocalDate plantedOn;
        private LocalDateTime harvestedAt;
    }
    @Data public static class Bridge {
        private Long packingId,batchId;
        private String sourceJson,sourceHash;
    }
    @Data public static class Issue {
        private Long id,packingId,recordedBy;
        private Integer count;
        private String requestKey,requestHash;
    }
}
