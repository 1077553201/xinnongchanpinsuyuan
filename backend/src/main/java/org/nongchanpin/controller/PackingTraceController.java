package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.IssuePackingCodesRequest;
import org.nongchanpin.service.PackingTraceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/packings/{id}/codes")
@RequiredArgsConstructor
public class PackingTraceController {
    private final PackingTraceService service;
    @GetMapping public CommonResponse detail(@PathVariable Long id,@RequestParam(defaultValue="1")int page) { return CommonResponse.ok(service.detail(id,page)); }
    @PostMapping public CommonResponse generate(@PathVariable Long id,@Valid @RequestBody IssuePackingCodesRequest request) { return CommonResponse.ok(service.generate(id,request)); }
}
