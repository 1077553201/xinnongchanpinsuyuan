package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.BindNfcRequest;
import org.nongchanpin.model.dto.DisableNfcRequest;
import org.nongchanpin.service.NfcService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nfc")
@RequiredArgsConstructor
public class NfcController {
    private final NfcService nfcService;

    @PostMapping("/bind")
    public CommonResponse bind(@Valid @RequestBody BindNfcRequest request) {
        return CommonResponse.ok(nfcService.bind(request));
    }

    @PostMapping("/{id}/disable")
    public CommonResponse disable(@PathVariable Long id, @RequestBody DisableNfcRequest request) {
        return CommonResponse.ok(nfcService.disable(id, request));
    }
}
