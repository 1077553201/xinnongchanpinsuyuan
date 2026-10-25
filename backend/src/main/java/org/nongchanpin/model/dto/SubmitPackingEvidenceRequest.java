package org.nongchanpin.model.dto;

import java.util.List;
import javax.validation.constraints.*;
import lombok.Data;

@Data
public class SubmitPackingEvidenceRequest {
    @NotBlank @Pattern(regexp="[0-9a-f]{64}") private String expectedManifestHash;
    @NotEmpty @Size(max=20) private List<@NotNull @Positive Long> itemIds;
}
