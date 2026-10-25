package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.ReconcileEvidenceRequest;
import org.nongchanpin.service.EvidenceReconciliationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/evidence-reconciliation")
@RequiredArgsConstructor
public class EvidenceReconciliationController {
    private final EvidenceReconciliationService service;
    @GetMapping public CommonResponse list(@RequestParam(defaultValue="")String kind,@RequestParam(defaultValue="ATTENTION")String status,
            @RequestParam(required=false)Long packingId,@RequestParam(defaultValue="")String code,
            @RequestParam(defaultValue="0")int minAgeHours,@RequestParam(defaultValue="1")int page) {
        return CommonResponse.ok(service.list(kind,status,packingId,code.trim(),minAgeHours,page));
    }
    @GetMapping("/{kind}/{id}") public CommonResponse inspect(@PathVariable String kind,@PathVariable Long id){return CommonResponse.ok(service.inspect(kind,id));}
    @PostMapping("/{kind}/{id}/reconcile") public CommonResponse reconcile(@PathVariable String kind,@PathVariable Long id,@Valid @RequestBody ReconcileEvidenceRequest request){return CommonResponse.ok(service.reconcile(kind,id,request.getExpectedCheckHash()));}
}
