package org.nongchanpin.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.nongchanpin.config.MqttProperties;
import org.nongchanpin.model.dto.TelemetryRequest;
import org.nongchanpin.service.IotTelemetryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "trace.mqtt", name = "enabled", havingValue = "true")
public class MqttTelemetryAdapter {
    private final MqttProperties mqttProperties;
    private final IotTelemetryService iotTelemetryService;
    private final ObjectMapper objectMapper;

    private MqttClient client;

    public boolean isConnected() { return client != null && client.isConnected(); }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(serverUri(), mqttProperties.getClientId(), new MemoryPersistence());
            client.setCallback(callback());
            client.connect(connectOptions());
        } catch (Exception ex) {
            log.warn("MQTT ingest disabled at runtime, broker unreachable: {}", ex.getMessage());
            client = null;
        }
    }

    @PreDestroy
    public void stop() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (Exception ex) {
            log.warn("failed to close MQTT client: {}", ex.getMessage());
        }
    }

    private MqttCallbackExtended callback() {
        return new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                subscribeAll();
                log.info("MQTT ingest connected to {} (reconnect={})", serverURI, reconnect);
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleMessage(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        };
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(mqttProperties.isCleanSession());
        options.setKeepAliveInterval(mqttProperties.getKeepAliveSeconds());
        options.setAutomaticReconnect(true);
        if (StringUtils.hasText(mqttProperties.getUsername())) {
            options.setUserName(mqttProperties.getUsername());
        }
        if (StringUtils.hasText(mqttProperties.getPassword())) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }
        return options;
    }

    private void subscribeAll() {
        subscribe(mqttProperties.getTelemetryTopic());
        subscribe(mqttProperties.getStatusTopic());
        subscribe(mqttProperties.getEventTopic());
    }

    private void subscribe(String topic) {
        if (client == null || !client.isConnected() || !StringUtils.hasText(topic)) {
            return;
        }
        try {
            client.subscribe(topic, mqttProperties.getQos());
        } catch (Exception ex) {
            log.warn("MQTT subscribe failed for {}: {}", topic, ex.getMessage());
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            if (matches(topic, mqttProperties.getTelemetryTopic())) {
                TelemetryRequest request = objectMapper.readValue(payload, TelemetryRequest.class);
                enrichFromTopic(topic, request);
                iotTelemetryService.ingest(request);
            } else if (matches(topic, mqttProperties.getStatusTopic())) {
                log.info("gateway status message on {}: {}", topic, payload);
            } else {
                log.info("gateway event message on {}: {}", topic, payload);
            }
        } catch (Exception ex) {
            log.warn("MQTT message rejected on {}: {}", topic, ex.getMessage());
        }
    }

    private void enrichFromTopic(String topic, TelemetryRequest request) {
        String[] segments = topic.split("/");
        if (segments.length >= 4) {
            if (request.getFarmId() == null) {
                request.setFarmId(parseLong(segments[1]));
            }
            if (!StringUtils.hasText(request.getGatewayCode())) {
                request.setGatewayCode(segments[2]);
            }
        }
    }

    private boolean matches(String topic, String filter) {
        if (!StringUtils.hasText(filter)) {
            return false;
        }
        String[] topicSegments = topic.split("/");
        String[] filterSegments = filter.split("/");
        if (topicSegments.length != filterSegments.length) {
            return false;
        }
        for (int i = 0; i < filterSegments.length; i++) {
            String filterSegment = filterSegments[i];
            if ("#".equals(filterSegment)) {
                return true;
            }
            if (!"+".equals(filterSegment) && !filterSegment.equals(topicSegments[i])) {
                return false;
            }
        }
        return true;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String serverUri() {
        return "tcp://" + mqttProperties.getHost() + ":" + mqttProperties.getPort();
    }
}
