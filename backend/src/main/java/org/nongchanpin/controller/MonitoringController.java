package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.service.MonitoringService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class MonitoringController {
    private final MonitoringService monitoring;
    @GetMapping("/devices") public CommonResponse devices() { return CommonResponse.ok(monitoring.devices()); }
    @PostMapping("/devices/{id}/assign") public CommonResponse assign(@PathVariable Long id, @Valid @RequestBody MonitoringService.Assign body) { monitoring.assign(id, body); return CommonResponse.ok(null); }
    @GetMapping("/bindings") public CommonResponse bindings() { return CommonResponse.ok(monitoring.bindings()); }
    @PostMapping("/bindings") public CommonResponse bind(@Valid @RequestBody MonitoringService.Bind body) { return CommonResponse.ok(monitoring.bind(body)); }
    @PostMapping("/bindings/{id}/end") public CommonResponse close(@PathVariable Long id, @Valid @RequestBody MonitoringService.Close body) { return CommonResponse.ok(monitoring.close(id, body)); }
    @GetMapping("/cycles/{id}/readings") public CommonResponse readings(@PathVariable Long id) { return CommonResponse.ok(monitoring.readings(id)); }
    @GetMapping("/cycles/{id}/windows") public CommonResponse windows(@PathVariable Long id) { return CommonResponse.ok(monitoring.windows(id)); }
}
