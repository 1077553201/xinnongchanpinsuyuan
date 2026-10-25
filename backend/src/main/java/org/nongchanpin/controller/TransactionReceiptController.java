package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.ReconcileEvidenceRequest;
import org.nongchanpin.service.TransactionReceiptService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/production/evidence-reconciliation/{kind}/{id}/receipt")
public class TransactionReceiptController {
    private final TransactionReceiptService service;
    @GetMapping public CommonResponse preview(@PathVariable String kind,@PathVariable Long id){return CommonResponse.ok(service.preview(kind,id));}
    @PostMapping public CommonResponse record(@PathVariable String kind,@PathVariable Long id,@Valid @RequestBody ReconcileEvidenceRequest request){return CommonResponse.ok(service.record(kind,id,request.getExpectedCheckHash()));}
}
