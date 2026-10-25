package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.SubmitPackingEvidenceRequest;
import org.nongchanpin.service.PackingEvidenceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/packings/{id}/evidence")
@RequiredArgsConstructor
public class PackingEvidenceController {
    private final PackingEvidenceService service;
    @GetMapping public CommonResponse preview(@PathVariable Long id) {return CommonResponse.ok(service.preview(id));}
    @PostMapping public CommonResponse submit(@PathVariable Long id,@Valid @RequestBody SubmitPackingEvidenceRequest request) {return CommonResponse.ok(service.submit(id,request));}
}
