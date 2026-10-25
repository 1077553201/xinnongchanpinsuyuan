package org.nongchanpin.model.dto;

import java.time.LocalDate;
import javax.validation.constraints.*;
import lombok.Data;

public final class SourceQualityRequests {
    private SourceQualityRequests() {}
    @Data public static class Create {
        @NotBlank @Size(max=64) private String reportNo;
        @NotBlank @Size(max=128) private String agencyName;
        @NotNull private LocalDate sampledOn;
        @NotNull private LocalDate checkedOn;
        @NotBlank @Pattern(regexp="PASS|FAIL|INCONCLUSIVE") private String declaredResult;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Publish {
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedManifestHash;
        @AssertTrue private boolean metadataReviewed;
        private boolean publishAttachment;
        private boolean attachmentPrivacyReviewed;
    }
    @Data public static class Withdraw {
        @NotBlank @Size(max=500) private String reason;
    }
}
