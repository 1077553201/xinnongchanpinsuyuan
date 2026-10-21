package org.nongchanpin.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.validation.constraints.*;
import lombok.Data;

public final class HarvestRequests {
    private HarvestRequests() {}
    @Data public static class Harvest {
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") private String lotCode;
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime harvestedAt;
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3) private BigDecimal quantity;
        @NotBlank @Pattern(regexp = "kg|g|jin") private String unit;
        @NotBlank @Size(max = 128) private String performerName;
        @Size(max = 2000) private String notes;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Grade {
        @NotBlank @Pattern(regexp = "SALEABLE|LOSS") private String kind;
        @NotBlank @Size(max = 128) private String gradeName;
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) private BigDecimal weightKg;
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime handledAt;
        @NotBlank @Size(max = 128) private String performerName;
        @Size(max = 256) private String location;
        @Size(max = 1000) private String reason;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Packing {
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") private String packingCode;
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime packedAt;
        @NotNull @Min(1) @Max(1000000) private Integer packageCount;
        @NotBlank @Size(max = 32) private String packageUnit;
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) private BigDecimal netWeightKg;
        @NotBlank @Size(max = 128) private String performerName;
        @NotBlank @Size(max = 256) private String location;
        @Size(max = 2000) private String notes;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{36}") private String requestKey;
    }
}
