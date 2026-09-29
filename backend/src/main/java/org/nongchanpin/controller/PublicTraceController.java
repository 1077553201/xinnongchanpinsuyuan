package org.nongchanpin.controller;

import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.service.PublicTraceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicTraceController {
    private final PublicTraceQueryService publicTraceQueryService;

    @GetMapping("/trace/{traceCode}")
    public CommonResponse getTrace(@PathVariable String traceCode) {
        return CommonResponse.ok(publicTraceQueryService.getTrace(traceCode));
    }
}
