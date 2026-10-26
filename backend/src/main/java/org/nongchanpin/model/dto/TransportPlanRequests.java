package org.nongchanpin.model.dto;

import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

public final class TransportPlanRequests {
    private TransportPlanRequests(){}
    @Data public static class Draft {
        @NotBlank @Pattern(regexp="CANCEL|REPLACE") private String action;
        @NotBlank @Size(max=800) private String reason;
        @Valid private TransportRequests.Create replacement;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class Confirm extends Draft {
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedImpactHash;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
