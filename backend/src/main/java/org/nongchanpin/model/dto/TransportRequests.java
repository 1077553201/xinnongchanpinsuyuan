package org.nongchanpin.model.dto;

import java.time.LocalDateTime;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

public final class TransportRequests {
    private TransportRequests() {}
    @Data public static class Create {
        @NotNull @Positive private Long dispatchId;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String shipmentCode;
        @NotBlank @Size(max=128) private String carrier;
        @Size(max=128) private String trackingReference;
        @NotBlank @Size(max=128) private String driverName;
        @Size(max=128) private String driverContact;
        @Size(max=1000) private String notes;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Event {
        @NotBlank @Pattern(regexp="DEPART|UPDATE|EXCEPTION|RECEIVE") private String kind;
        @NotNull @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime occurredAt;
        @NotBlank @Size(max=256) private String location;
        @NotBlank @Size(max=128) private String performerName;
        @NotBlank @Size(max=1000) private String description;
        @Size(max=128) private String reference;
        @Min(0) @Max(1000000) private Integer acceptedCount;
        @Min(0) @Max(1000000) private Integer rejectedCount;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
