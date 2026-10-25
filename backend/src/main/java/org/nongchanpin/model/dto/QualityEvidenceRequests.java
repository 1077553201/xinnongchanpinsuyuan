package org.nongchanpin.model.dto;

import javax.validation.constraints.*;
import lombok.Data;

public final class QualityEvidenceRequests {
    private QualityEvidenceRequests() {}
    @Data public static class Replace {
        @NotNull @Positive private Long previousReportId;
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedPreviousHash;
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedReplacementHash;
        @NotBlank @Size(max=500) private String reason;
    }
    @Data public static class Submit {
        @NotBlank @Size(max=128) private String traceCode;
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedManifestHash;
    }
}
