package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.RejectionRequests.*;
import org.nongchanpin.service.RejectionHandlingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/production/rejections")
public class RejectionHandlingController {
    private final RejectionHandlingService service;
    @GetMapping public CommonResponse list(@RequestParam(required=false)Long warehouseId,@RequestParam(defaultValue="ALL")String status,@RequestParam(defaultValue="")String code,@RequestParam(defaultValue="1")int page){return CommonResponse.ok(service.list(warehouseId,status,code.trim(),page));}
    @GetMapping("/preview") public CommonResponse preview(@RequestParam Long dispatchId,@RequestParam Long itemId){return CommonResponse.ok(service.preview(dispatchId,itemId));}
    @GetMapping("/{id}") public CommonResponse detail(@PathVariable Long id){return CommonResponse.ok(service.detail(id));}
    @PostMapping public CommonResponse create(@Valid @RequestBody Create request){return CommonResponse.ok(service.create(request));}
    @PostMapping("/{id}/events") public CommonResponse append(@PathVariable Long id,@Valid @RequestBody Append request){return CommonResponse.ok(service.append(id,request));}
}
