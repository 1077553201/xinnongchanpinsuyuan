package org.nongchanpin.model.dto;

import javax.validation.constraints.*;
import lombok.Data;

@Data
public class OrganizationRequest {
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") private String orgCode;
    @NotBlank @Size(max = 128) private String orgName;
    @NotBlank @Size(max = 32) private String orgType;
    @Size(max = 64) private String contactName;
    @Size(max = 32) private String contactPhone;
    @Size(max = 255) private String address;
}
