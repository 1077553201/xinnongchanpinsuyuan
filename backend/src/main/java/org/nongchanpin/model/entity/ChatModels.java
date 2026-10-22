package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

public final class ChatModels {
    private ChatModels() {}
    @Data public static class Conversation {
        private Long id,ownerId;
        private String title;
        private LocalDateTime createdAt,updatedAt;
    }
    @Data public static class Turn {
        private Long id,conversationId,reportId;
        private String userText,replyText,evidenceJson,status,errorMessage;
        private LocalDateTime createdAt,finishedAt,leaseUntil;
        private String requestKey;
        @JsonIgnore private String requestHash;
        @JsonIgnore private String attemptToken;
        @JsonIgnore private String proposalHash;
    }
}
