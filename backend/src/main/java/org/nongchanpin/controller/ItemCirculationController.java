package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.ItemCirculationRequests;
import org.nongchanpin.service.ItemCirculationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/sales/dispatches/{id}/items")
@RequiredArgsConstructor
public class ItemCirculationController {
    private final ItemCirculationService service;
    @GetMapping public CommonResponse detail(@PathVariable Long id,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="AVAILABLE")String mode,@RequestParam(defaultValue="1")int page){return CommonResponse.ok(service.detail(id,search,mode,page));}
    @PostMapping public CommonResponse link(@PathVariable Long id,@Valid @RequestBody ItemCirculationRequests.Link request){return CommonResponse.ok(service.link(id,request));}
    @PostMapping("/receipt") public CommonResponse receipt(@PathVariable Long id,@Valid @RequestBody ItemCirculationRequests.Receipt request){return CommonResponse.ok(service.receive(id,request));}
}
