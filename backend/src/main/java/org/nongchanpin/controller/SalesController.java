package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.*;
import org.nongchanpin.service.SalesService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/sales")
@RequiredArgsConstructor
public class SalesController {
    private final SalesService sales;
    @GetMapping public CommonResponse list(@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return CommonResponse.ok(sales.list(search,status,page,pageSize));}
    @PostMapping public CommonResponse create(@Valid @RequestBody SalesRequests.Create request){return CommonResponse.ok(sales.create(request));}
    @GetMapping("/{id}") public CommonResponse detail(@PathVariable Long id){return CommonResponse.ok(sales.detail(id));}
    @PostMapping("/{id}/close") public CommonResponse close(@PathVariable Long id,@Valid @RequestBody GrowingRequests.Reason request){return CommonResponse.ok(sales.close(id,request));}
    @PostMapping("/lines/{id}/dispatch") public CommonResponse dispatch(@PathVariable Long id,@Valid @RequestBody SalesRequests.Dispatch request){return CommonResponse.ok(sales.dispatch(id,request));}
}
