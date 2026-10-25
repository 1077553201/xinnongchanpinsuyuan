package org.nongchanpin.model.dto;

import javax.validation.constraints.*;
import lombok.Data;

@Data
public class PackingNfcRequest {
    @Data public static class Preview {
        @NotBlank @Pattern(regexp="BIND|DISABLE") private String operation;
        @Size(max=64) private String uid;
        @Size(max=500) private String reason;
    }
    @NotBlank @Pattern(regexp="BIND|DISABLE") private String operation;
    @Size(max=64) private String uid;
    @Size(max=500) private String reason;
    @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedHash;
}
