package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.CreatePlotRequest;
import org.nongchanpin.model.dto.StartPlantingRequest;
import org.nongchanpin.model.dto.EndPlantingRequest;
import org.nongchanpin.service.ProductionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {
    private final ProductionService production;

    @GetMapping("/organizations")
    public CommonResponse organizations() { return CommonResponse.ok(production.organizations()); }

    @GetMapping("/plots")
    public CommonResponse plots() { return CommonResponse.ok(production.plots()); }

    @PostMapping("/plots")
    public CommonResponse create(@Valid @RequestBody CreatePlotRequest request) {
        return CommonResponse.ok(production.createPlot(request));
    }

    @GetMapping("/cycles")
    public CommonResponse cycles() { return CommonResponse.ok(production.cycles()); }

    @PostMapping("/plots/{id}/cycles")
    public CommonResponse start(@PathVariable Long id, @Valid @RequestBody StartPlantingRequest request) {
        return CommonResponse.ok(production.start(id, request));
    }

    @PostMapping("/cycles/{id}/end")
    public CommonResponse end(@PathVariable Long id, @Valid @RequestBody EndPlantingRequest request) {
        return CommonResponse.ok(production.end(id, request));
    }
}
