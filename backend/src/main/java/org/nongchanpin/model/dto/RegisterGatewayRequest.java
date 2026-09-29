package org.nongchanpin.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterGatewayRequest {
    @NotNull
    private Long farmId;
    @NotBlank
    private String gatewayCode;
    private String gatewayName;
    private String mqttClientId;
}
