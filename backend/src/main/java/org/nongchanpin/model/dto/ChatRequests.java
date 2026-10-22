package org.nongchanpin.model.dto;

import javax.validation.constraints.*;
import lombok.Data;

public final class ChatRequests {
    private ChatRequests() {}
    @Data public static class Create {
        @Size(max=100) private String title;
    }
    @Data public static class Message {
        @NotBlank @Size(max=4000) private String text;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
