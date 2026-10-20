package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.AggregateWindowRequest;
import org.nongchanpin.model.dto.RegisterDeviceRequest;
import org.nongchanpin.model.dto.RegisterGatewayRequest;
import org.nongchanpin.model.dto.TelemetryRequest;
import org.nongchanpin.service.IotDeviceService;
import org.nongchanpin.service.IotQueryService;
import org.nongchanpin.service.IotTelemetryService;
import org.nongchanpin.service.IotWindowAggregationService;
import org.nongchanpin.config.MqttProperties;
import org.nongchanpin.iot.MqttTelemetryAdapter;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iot")
@RequiredArgsConstructor
public class IotController {
    private final IotTelemetryService telemetryService;
    private final IotDeviceService deviceService;
    private final IotQueryService queryService;
    private final IotWindowAggregationService windowAggregationService;
    private final MqttProperties mqttProperties;
    private final ObjectProvider<MqttTelemetryAdapter> mqttAdapter;

    @GetMapping("/status")
    public CommonResponse status() {
        MqttTelemetryAdapter adapter = mqttAdapter.getIfAvailable();
        return CommonResponse.ok(Map.of("mqttEnabled", mqttProperties.isEnabled(), "mqttConnected", adapter != null && adapter.isConnected()));
    }

    @PostMapping("/telemetry/mock")
    public CommonResponse mockTelemetry(@RequestBody TelemetryRequest request) {
        return CommonResponse.ok(telemetryService.ingest(request));
    }

    @PostMapping("/gateways")
    public CommonResponse registerGateway(@Valid @RequestBody RegisterGatewayRequest request) {
        return CommonResponse.ok(deviceService.registerGateway(request));
    }

    @GetMapping("/gateways")
    public CommonResponse listGateways() {
        return CommonResponse.ok(deviceService.listGateways());
    }

    @PostMapping("/devices")
    public CommonResponse registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        return CommonResponse.ok(deviceService.registerDevice(request));
    }

    @GetMapping("/devices")
    public CommonResponse listDevices() {
        return CommonResponse.ok(deviceService.listDevices());
    }

    @GetMapping("/readings")
    public CommonResponse listReadings(@RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) Integer limit) {
        return CommonResponse.ok(queryService.listReadings(deviceCode, metric, limit));
    }

    @GetMapping("/windows")
    public CommonResponse listWindows(@RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) Integer limit) {
        return CommonResponse.ok(queryService.listWindows(deviceCode, limit));
    }

    @PostMapping("/windows/aggregate")
    public CommonResponse aggregate(@Valid @RequestBody AggregateWindowRequest request) {
        return CommonResponse.ok(windowAggregationService.aggregate(request.getDeviceCode(), request.getMetric(),
                request.getFarmId(), request.getGatewayCode(), request.getWindowStart(), request.getWindowEnd()));
    }

    @GetMapping("/alerts")
    public CommonResponse listAlerts(@RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) Integer limit) {
        return CommonResponse.ok(queryService.listAlerts(deviceCode, limit));
    }
}
