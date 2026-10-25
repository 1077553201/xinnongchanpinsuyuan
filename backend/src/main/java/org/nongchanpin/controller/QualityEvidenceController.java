package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.QualityEvidenceRequests.*;
import org.nongchanpin.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/quality-reports/{id}")
@RequiredArgsConstructor
public class QualityEvidenceController {
    private final QualityEvidenceService evidence;
    private final QualityRevisionService revisions;
    @GetMapping("/evidence") public CommonResponse preview(@PathVariable Long id,@RequestParam(defaultValue="")String traceCode){return CommonResponse.ok(evidence.preview(id,traceCode));}
    @PostMapping("/evidence") public CommonResponse submit(@PathVariable Long id,@Valid @RequestBody Submit request){return CommonResponse.ok(evidence.submit(id,request));}
    @GetMapping("/replacement") public CommonResponse replacement(@PathVariable Long id,@RequestParam(required=false)Long previousReportId){return CommonResponse.ok(revisions.detail(id,previousReportId));}
    @PostMapping("/replacement") public CommonResponse link(@PathVariable Long id,@Valid @RequestBody Replace request){return CommonResponse.ok(revisions.link(id,request));}
}
