package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.TransportRequests;
import org.nongchanpin.service.TransportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/transport")
@RequiredArgsConstructor
public class TransportController {
    private final TransportService transport;
    @GetMapping public CommonResponse list(@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return CommonResponse.ok(transport.list(search,status,page,pageSize));}
    @GetMapping("/{id}") public CommonResponse detail(@PathVariable Long id){return CommonResponse.ok(transport.detail(id));}
    @PostMapping public CommonResponse create(@Valid @RequestBody TransportRequests.Create request){return CommonResponse.ok(transport.create(request));}
    @PostMapping("/{id}/events") public CommonResponse record(@PathVariable Long id,@Valid @RequestBody TransportRequests.Event request){return CommonResponse.ok(transport.record(id,request));}
}
