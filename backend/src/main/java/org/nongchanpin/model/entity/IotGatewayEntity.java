package org.nongchanpin.model.entity;

import lombok.Data;

@Data
public class IotGatewayEntity {
    private Long id;
    private Long farmId;
    private String gatewayCode;
    private String gatewayName;
    private String mqttClientId;
    private String status;
}
