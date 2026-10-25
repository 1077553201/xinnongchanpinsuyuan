package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

public final class ItemCirculationModels {
    private ItemCirculationModels() {}
    @Data public static class Item {
        private Long itemId, dispatchId;
        private String traceCode, receiptOutcome;
        private LocalDateTime linkedAt, receiptAt;
    }
    @Data public static class Operation {
        @JsonIgnore private String requestKey, requestHash;
        private Long dispatchId, recordedBy;
    }
}
