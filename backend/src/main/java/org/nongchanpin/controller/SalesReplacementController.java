package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.SalesReplacementRequests;
import org.nongchanpin.service.SalesReplacementService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/production/sales/{id}/replacement")
public class SalesReplacementController {
    private final SalesReplacementService service;
    @GetMapping public CommonResponse detail(@PathVariable Long id){return CommonResponse.ok(service.detail(id));}
    @PostMapping("/preview") public CommonResponse preview(@PathVariable Long id,@Valid @RequestBody SalesReplacementRequests.Draft request){return CommonResponse.ok(service.preview(id,request));}
    @PostMapping public CommonResponse confirm(@PathVariable Long id,@Valid @RequestBody SalesReplacementRequests.Confirm request){return CommonResponse.ok(service.confirm(id,request));}
}
