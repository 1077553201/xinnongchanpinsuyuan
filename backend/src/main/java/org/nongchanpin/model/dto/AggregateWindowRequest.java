package org.nongchanpin.model.dto;

import java.time.LocalDateTime;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AggregateWindowRequest {
    @NotBlank
    private String deviceCode;
    @NotBlank
    private String metric;
    @NotNull
    private LocalDateTime windowStart;
    @NotNull
    private LocalDateTime windowEnd;
    private Long farmId;
    private String gatewayCode;
}
