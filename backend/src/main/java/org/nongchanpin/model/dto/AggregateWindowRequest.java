package org.nongchanpin.model.dto;

import java.time.LocalDateTime;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class AggregateWindowRequest {
    @NotBlank
    private String deviceCode;
    @NotBlank
    private String metric;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime windowStart;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime windowEnd;
    private Long farmId;
    private String gatewayCode;
}
