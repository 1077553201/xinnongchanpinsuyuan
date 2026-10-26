package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.TransportPlanRequests;
import org.nongchanpin.service.TransportPlanService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/production/transport/{id}/plans")
public class TransportPlanController {
    private final TransportPlanService service;
    @GetMapping public CommonResponse detail(@PathVariable Long id){return CommonResponse.ok(service.detail(id));}
    @PostMapping("/preview") public CommonResponse preview(@PathVariable Long id,@Valid @RequestBody TransportPlanRequests.Draft request){return CommonResponse.ok(service.preview(id,request));}
    @PostMapping public CommonResponse confirm(@PathVariable Long id,@Valid @RequestBody TransportPlanRequests.Confirm request){return CommonResponse.ok(service.confirm(id,request));}
}
