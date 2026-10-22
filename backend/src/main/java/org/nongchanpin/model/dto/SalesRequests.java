package org.nongchanpin.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

public final class SalesRequests {
    private SalesRequests() {}
    @Data public static class Create {
        @NotNull @Positive private Long organizationId;
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String orderCode;
        @NotNull private LocalDate orderedOn;
        @NotBlank @Size(max=128) private String customerName;
        @Size(max=128) private String customerContact;
        @NotBlank @Size(max=500) private String deliveryAddress;
        @Size(max=2000) private String notes;
        @NotNull @Size(min=1,max=50) @Valid private List<@NotNull Line> lines;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Line {
        @NotNull @Positive private Long packingId;
        @NotNull @Positive private Long warehouseId;
        @NotNull @Min(1) @Max(1000000) private Integer packageCount;
        @NotNull @DecimalMin("0") @Digits(integer=9,fraction=2) private BigDecimal unitPrice;
    }
    @Data public static class Dispatch {
        @NotNull @Min(1) @Max(1000000) private Integer packageCount;
        @NotNull @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime occurredAt;
        @NotBlank @Size(max=128) private String performerName;
        @NotBlank @Size(max=1000) private String reason;
        @Size(max=128) private String reference;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
