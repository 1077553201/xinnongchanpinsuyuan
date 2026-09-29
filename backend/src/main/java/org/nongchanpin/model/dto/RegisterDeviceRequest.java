package org.nongchanpin.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterDeviceRequest {
    @NotNull
    private Long farmId;
    @NotBlank
    private String gatewayCode;
    @NotBlank
    private String deviceCode;
    private String deviceName;
    private String deviceType;
}
