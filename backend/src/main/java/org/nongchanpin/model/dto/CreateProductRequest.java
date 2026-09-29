package org.nongchanpin.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProductRequest {
    @NotNull
    private Long organizationId;
    @NotBlank
    private String productCode;
    @NotBlank
    private String productName;
    @NotBlank
    private String category;
    private String variety;
    private String originPlace;
    private String description;
}
