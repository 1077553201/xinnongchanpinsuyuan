package org.nongchanpin.model.dto;

import java.time.LocalDateTime;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

public final class RejectionRequests {
    private RejectionRequests() {}
    @Data public static class Create {
        @NotNull @Positive private Long dispatchId;
        @NotNull @Positive private Long itemId;
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedSourceHash;
        @NotBlank @Size(max=500) private String reason;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Append {
        @NotBlank @Pattern(regexp="RETURN_DISPATCH|QUARANTINE_RECEIVE|DISPOSE|ANNOTATION") private String kind;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime occurredAt;
        @Positive private Long warehouseId;
        @Size(max=256) private String quarantineLocation;
        @NotBlank @Size(max=128) private String performerName;
        @NotBlank @Size(max=1000) private String note;
        @Size(max=128) private String reference;
        @Min(0) @Max(100) private Integer targetRevision;
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedStateHash;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
