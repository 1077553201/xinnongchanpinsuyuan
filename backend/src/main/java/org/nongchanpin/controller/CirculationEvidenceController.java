package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.SubmitCirculationEvidenceRequest;
import org.nongchanpin.service.CirculationEvidenceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/items/{id}/circulation-evidence")
@RequiredArgsConstructor
public class CirculationEvidenceController {
    private final CirculationEvidenceService service;
    @GetMapping public CommonResponse preview(@PathVariable Long id){return CommonResponse.ok(service.preview(id));}
    @PostMapping public CommonResponse submit(@PathVariable Long id,@Valid @RequestBody SubmitCirculationEvidenceRequest request){return CommonResponse.ok(service.submit(id,request));}
}
