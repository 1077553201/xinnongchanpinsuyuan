package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.PackingNfcRequest;
import org.nongchanpin.service.PackingNfcService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PackingNfcController {
    private final PackingNfcService service;
    @GetMapping("/api/production/packing-items/{id}/nfc")
    public CommonResponse detail(@PathVariable Long id) { return CommonResponse.ok(service.detail(id,null,null,null)); }
    @PostMapping("/api/production/packing-items/{id}/nfc/preview")
    public CommonResponse preview(@PathVariable Long id,@Valid @RequestBody PackingNfcRequest.Preview request) {
        return CommonResponse.ok(service.detail(id,request.getOperation(),request.getUid(),request.getReason()));
    }
    @PostMapping("/api/production/packing-items/{id}/nfc")
    public CommonResponse submit(@PathVariable Long id,@Valid @RequestBody PackingNfcRequest request) {
        return CommonResponse.ok(service.submit(id,request));
    }
    @GetMapping("/api/public/trace/nfc/{uidHash}")
    public CommonResponse resolve(@PathVariable String uidHash){return CommonResponse.ok(service.resolve(uidHash));}
}
