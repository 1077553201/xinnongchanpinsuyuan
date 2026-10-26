package org.nongchanpin.model.dto;

import java.time.LocalDateTime;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
public class LossCorrectionRequest {
    @NotNull @Min(0) @Max(1000000) private Integer correctedCount;
    @NotNull @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime verifiedAt;
    @NotBlank @Size(max=128) private String performerName;
    @NotBlank @Size(max=1000) private String reason;
    @Size(max=128) private String reference;
    @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedImpactHash;
    @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
}
