package org.nongchanpin.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class AgentModels {
    private AgentModels() {}
    @Data public static class Report {
        private Long id,ownerId;
        private LocalDate reportDate;
        private String sourceText,mode,status;
        private Integer version;
        private LocalDateTime createdAt;
        @JsonIgnore private String sourceHash;
        @JsonIgnore private String requestKey;
    }
    @Data public static class Operation {
        private String id,kind,quote;
        private Long targetId;
        private ObjectNode fields;
        private List<String> issues=new ArrayList<>();
        private String contextHash;
    }
    @Data public static class Confirmation {
        private Long id,reportId,confirmedBy;
        private Integer version;
        private String scopeJson,status;
        private LocalDateTime confirmedAt;
        @JsonIgnore private String requestKey;
        @JsonIgnore private String requestHash;
    }
    @Data public static class Receipt {
        private Long id,confirmationId,businessId;
        private String operationId,status,message;
        private LocalDateTime recordedAt;
    }
}
