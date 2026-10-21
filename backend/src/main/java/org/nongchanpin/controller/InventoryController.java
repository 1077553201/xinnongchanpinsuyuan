package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.InventoryRequests;
import org.nongchanpin.service.InventoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventory;
    @GetMapping("/warehouses") public CommonResponse warehouses() { return CommonResponse.ok(inventory.warehouses()); }
    @PostMapping("/warehouses") public CommonResponse warehouse(@Valid @RequestBody InventoryRequests.Warehouse request) { return CommonResponse.ok(inventory.createWarehouse(request)); }
    @GetMapping("/cycles/{id}/inventory") public CommonResponse sources(@PathVariable Long id) { return CommonResponse.ok(inventory.sources(id)); }
    @GetMapping("/inventory/packings/{id}") public CommonResponse detail(@PathVariable Long id) { return CommonResponse.ok(inventory.detail(id)); }
    @PostMapping("/inventory/packings/{id}/movements") public CommonResponse record(@PathVariable Long id,@Valid @RequestBody InventoryRequests.Movement request) { return CommonResponse.ok(inventory.record(id,request)); }
}
