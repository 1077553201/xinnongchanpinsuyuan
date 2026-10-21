package org.nongchanpin.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

public final class GrowingRequests {
    private GrowingRequests() {}
    @Data public static class Rule {
        @NotBlank @Pattern(regexp = "temperature|humidity|soil_moisture|light") private String metric;
        @Digits(integer = 12, fraction = 6) private BigDecimal minValue;
        @Digits(integer = 12, fraction = 6) private BigDecimal maxValue;
        @NotNull @Min(1) @Max(1440) private Integer expectedIntervalMinutes;
        @NotBlank @Size(max = 1000) private String basis;
        @Size(max = 2000) private String notes;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Task {
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") private String taskCode;
        @NotBlank @Pattern(regexp = "IRRIGATION|FERTILIZATION|PEST_CONTROL|PRUNING|WEEDING|INSPECTION|OTHER") private String taskType;
        @NotBlank @Size(max = 128) private String title;
        @NotNull private LocalDate plannedOn;
        @NotNull @Positive private Long assignedTo;
        @Size(max = 2000) private String instructions;
    }
    @Data public static class Execution {
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime performedAt;
        @Size(max = 128) private String inputName;
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) private BigDecimal quantity;
        @Size(max = 32) private String unit;
        @NotBlank @Size(max = 2000) private String notes;
        @Size(max = 500) private String evidenceReference;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Reason { @NotBlank @Size(max = 1000) private String reason; }
    @Data public static class HandleCase {
        @NotBlank @Size(max = 2000) private String actionTaken;
        @Size(max = 2000) private String observation;
    }
}
