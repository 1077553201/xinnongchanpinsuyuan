package org.nongchanpin.model.dto;

import javax.validation.constraints.*;
import lombok.Data;

@Data
public class IssuePackingCodesRequest {
    @NotNull @Min(1) @Max(100) private Integer count;
    @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") private String requestKey;
}
