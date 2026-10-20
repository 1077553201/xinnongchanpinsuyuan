package org.nongchanpin.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProductRequest {
    @NotNull
    @Positive
    private Long organizationId;
    @NotBlank
    @Size(max = 64)
    private String productCode;
    @NotBlank
    @Size(max = 128)
    private String productName;
    @NotBlank
    @Size(max = 64)
    private String category;
    @Size(max = 128)
    private String variety;
    @Size(max = 255)
    private String originPlace;
    @Size(max = 5000)
    private String description;
}
