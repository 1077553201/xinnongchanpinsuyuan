package org.nongchanpin.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "trace.iot")
public class IotProperties {
    private int windowMinutes = 5;
    private int offlineTimeoutSeconds = 300;
    private boolean autoRegisterGateway = true;
    private boolean autoRegisterDevice = true;
    private Map<String, AlertRange> alerts = new LinkedHashMap<>();

    @Data
    public static class AlertRange {
        private BigDecimal min;
        private BigDecimal max;
    }
}
