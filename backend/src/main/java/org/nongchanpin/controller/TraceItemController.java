package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.GenerateItemsRequest;
import org.nongchanpin.service.TraceItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class TraceItemController {
    private final TraceItemService traceItemService;

    @PostMapping("/generate")
    public CommonResponse generate(@Valid @RequestBody GenerateItemsRequest request) {
        return CommonResponse.ok(traceItemService.generate(request));
    }

    @GetMapping
    public CommonResponse findAll() {
        return CommonResponse.ok(traceItemService.findAll());
    }

    @GetMapping("/{id}")
    public CommonResponse get(@PathVariable Long id) {
        return CommonResponse.ok(traceItemService.get(id));
    }
}
