package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.CloseBatchRequest;
import org.nongchanpin.model.dto.CreateBatchRequest;
import org.nongchanpin.service.BatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {
    private final BatchService batchService;

    @PostMapping
    public CommonResponse create(@Valid @RequestBody CreateBatchRequest request) {
        return CommonResponse.ok(batchService.create(request));
    }

    @GetMapping
    public CommonResponse findAll() {
        return CommonResponse.ok(batchService.findAll());
    }

    @GetMapping("/{id}")
    public CommonResponse get(@PathVariable Long id) {
        return CommonResponse.ok(batchService.get(id));
    }

    @PostMapping("/{id}/close")
    public CommonResponse close(@PathVariable Long id, @Valid @RequestBody CloseBatchRequest request) {
        return CommonResponse.ok(batchService.close(id, request));
    }
}
