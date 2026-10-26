package org.nongchanpin.controller;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.OrganizationRequest;
import org.nongchanpin.service.OrganizationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService service;
    @Data public static class Status { @NotBlank private String status; }
    @GetMapping public CommonResponse list(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "1") int page) { return CommonResponse.ok(service.list(search, status, page)); }
    @GetMapping("/{id}") public CommonResponse detail(@PathVariable Long id) { return CommonResponse.ok(service.detail(id)); }
    @PostMapping public CommonResponse create(@Valid @RequestBody OrganizationRequest request) { return CommonResponse.ok(service.create(request)); }
    @PostMapping("/{id}") public CommonResponse update(@PathVariable Long id, @Valid @RequestBody OrganizationRequest request) { return CommonResponse.ok(service.update(id, request)); }
    @PostMapping("/{id}/status") public CommonResponse status(@PathVariable Long id, @Valid @RequestBody Status request) { return CommonResponse.ok(service.status(id, request.getStatus())); }
}
