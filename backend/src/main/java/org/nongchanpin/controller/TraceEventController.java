package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.CreateTraceEventRequest;
import org.nongchanpin.service.TraceEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items/{itemId}/events")
@RequiredArgsConstructor
public class TraceEventController {
    private final TraceEventService traceEventService;

    @PostMapping
    public CommonResponse append(@PathVariable Long itemId, @Valid @RequestBody CreateTraceEventRequest request) {
        return CommonResponse.ok(traceEventService.append(itemId, request));
    }

    @GetMapping
    public CommonResponse findByItemId(@PathVariable Long itemId) {
        return CommonResponse.ok(traceEventService.findByItemId(itemId));
    }
}
