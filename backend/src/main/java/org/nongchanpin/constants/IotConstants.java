package org.nongchanpin.constants;

public final class IotConstants {
    public static final String METRIC_TEMPERATURE = "temperature";
    public static final String METRIC_HUMIDITY = "humidity";
    public static final String METRIC_SOIL_MOISTURE = "soil_moisture";
    public static final String METRIC_LIGHT = "light";

    public static final String ALERT_THRESHOLD_HIGH = "THRESHOLD_HIGH";
    public static final String ALERT_THRESHOLD_LOW = "THRESHOLD_LOW";
    public static final String ALERT_DEVICE_OFFLINE = "DEVICE_OFFLINE";

    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_CRITICAL = "CRITICAL";

    public static final String ALERT_STATUS_OPEN = "OPEN";

    public static final String DEVICE_STATUS_ACTIVE = "ACTIVE";
    public static final String DEVICE_STATUS_OFFLINE = "OFFLINE";

    public static final String GATEWAY_STATUS_ACTIVE = "ACTIVE";
    public static final String READING_STATUS_RAW = "RAW";
    public static final String WINDOW_CHAIN_BUSINESS_TYPE = "SENSOR_WINDOW_APPEND";
    public static final String WINDOW_CHAIN_EVENT_NAME = "SensorWindowAppended";

    private IotConstants() {
    }
}
