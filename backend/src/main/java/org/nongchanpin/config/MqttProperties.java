package org.nongchanpin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "trace.mqtt")
public class MqttProperties {
    private boolean enabled = false;
    private String host = "127.0.0.1";
    private int port = 1883;
    private String username;
    private String password;
    private String clientId = "agri-trace-backend-local";
    private String telemetryTopic = "agri/+/+/telemetry";
    private String statusTopic = "agri/+/+/status";
    private String eventTopic = "agri/+/+/event";
    private int qos = 1;
    private int keepAliveSeconds = 30;
    private boolean cleanSession = true;
}
