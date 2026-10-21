package org.nongchanpin.model.dto;

import java.math.BigDecimal;
import javax.validation.constraints.*;
import lombok.Data;

@Data
public class CreatePlotRequest {
    @NotNull @Positive
    private Long organizationId;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    private String plotCode;
    @NotBlank @Size(max = 128)
    private String plotName;
    @NotBlank @Size(max = 255)
    private String location;
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
    private BigDecimal areaMu;
    @NotBlank @Size(max = 64)
    private String managerName;
    @Size(max = 2000)
    private String notes;
}
