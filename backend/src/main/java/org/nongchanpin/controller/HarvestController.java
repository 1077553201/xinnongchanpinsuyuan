package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.HarvestRequests;
import org.nongchanpin.service.HarvestService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class HarvestController {
    private final HarvestService harvest;
    @GetMapping("/cycles/{id}/harvests") public CommonResponse list(@PathVariable Long id) { return CommonResponse.ok(harvest.list(id)); }
    @PostMapping("/cycles/{id}/harvests") public CommonResponse create(@PathVariable Long id, @Valid @RequestBody HarvestRequests.Harvest request) { return CommonResponse.ok(harvest.create(id, request)); }
    @GetMapping("/harvests/{id}") public CommonResponse detail(@PathVariable Long id) { return CommonResponse.ok(harvest.detail(id)); }
    @PostMapping("/harvests/{id}/grades") public CommonResponse grade(@PathVariable Long id, @Valid @RequestBody HarvestRequests.Grade request) { return CommonResponse.ok(harvest.grade(id, request)); }
    @PostMapping("/harvests/{id}/reconcile") public CommonResponse finish(@PathVariable Long id) { return CommonResponse.ok(harvest.finish(id)); }
    @PostMapping("/grades/{id}/packings") public CommonResponse pack(@PathVariable Long id, @Valid @RequestBody HarvestRequests.Packing request) { return CommonResponse.ok(harvest.pack(id, request)); }
}
