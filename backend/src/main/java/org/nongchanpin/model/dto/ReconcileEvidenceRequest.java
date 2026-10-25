package org.nongchanpin.model.dto;

import javax.validation.constraints.*;
import lombok.Data;

@Data
public class ReconcileEvidenceRequest {
    @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedCheckHash;
}
