package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.LossCorrectionRequest;
import org.nongchanpin.service.LossCorrectionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/production/inventory/movements/{id}/corrections")
public class LossCorrectionController {
    private final LossCorrectionService service;
    @GetMapping public CommonResponse detail(@PathVariable Long id){return CommonResponse.ok(service.detail(id));}
    @GetMapping("/preview") public CommonResponse preview(@PathVariable Long id,@RequestParam int correctedCount){return CommonResponse.ok(service.preview(id,correctedCount));}
    @PostMapping public CommonResponse confirm(@PathVariable Long id,@Valid @RequestBody LossCorrectionRequest request){return CommonResponse.ok(service.confirm(id,request));}
}
