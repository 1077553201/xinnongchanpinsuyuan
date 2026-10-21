package org.nongchanpin.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import javax.validation.constraints.*;
import lombok.Data;

public final class InventoryRequests {
    private InventoryRequests() {}
    @Data public static class Warehouse {
        @NotNull @Positive private Long organizationId;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String warehouseCode;
        @NotBlank @Size(max=128) private String warehouseName;
        @NotBlank @Size(max=256) private String location;
        @Size(max=1000) private String notes;
    }
    @Data public static class Movement {
        @NotNull @Positive private Long warehouseId;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String movementCode;
        @NotBlank @Pattern(regexp="IN|OUT|LOSS") private String kind;
        @NotNull @Min(1) @Max(1000000) private Integer packageCount;
        @NotNull @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime occurredAt;
        @NotBlank @Size(max=128) private String performerName;
        @Size(max=256) private String destination;
        @NotBlank @Size(max=1000) private String reason;
        @Size(max=128) private String reference;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
