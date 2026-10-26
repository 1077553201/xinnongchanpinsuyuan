package org.nongchanpin.model.dto;

import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

public final class SalesReplacementRequests {
    private SalesReplacementRequests(){}
    @Data public static class Draft {
        @NotNull @Valid private SalesRequests.Create replacement;
        @NotBlank @Size(max=800) private String reason;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class Confirm extends Draft {
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedImpactHash;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
