package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.GrowingRequests;
import org.nongchanpin.service.FarmTaskService;
import org.nongchanpin.service.GrowingEnvironmentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class GrowingController {
    private final GrowingEnvironmentService environment;
    private final FarmTaskService tasks;
    @GetMapping("/people") public CommonResponse people() { return CommonResponse.ok(tasks.people()); }
    @GetMapping("/cycles/{id}/environment") public CommonResponse environment(@PathVariable Long id) { return CommonResponse.ok(environment.overview(id)); }
    @PostMapping("/cycles/{id}/environment/rules") public CommonResponse rule(@PathVariable Long id, @Valid @RequestBody GrowingRequests.Rule request) { return CommonResponse.ok(environment.configure(id, request)); }
    @PostMapping("/environment/rules/{id}/retire") public CommonResponse retire(@PathVariable Long id, @Valid @RequestBody GrowingRequests.Reason request) { environment.retire(id, request); return CommonResponse.ok(null); }
    @GetMapping("/cycles/{id}/environment/cases") public CommonResponse cases(@PathVariable Long id) { return CommonResponse.ok(environment.cases(id)); }
    @PostMapping("/environment/cases/{id}/handle") public CommonResponse handle(@PathVariable Long id, @Valid @RequestBody GrowingRequests.HandleCase request) { return CommonResponse.ok(environment.handle(id, request)); }
    @GetMapping("/cycles/{id}/tasks") public CommonResponse tasks(@PathVariable Long id) { return CommonResponse.ok(tasks.tasks(id)); }
    @PostMapping("/cycles/{id}/tasks") public CommonResponse createTask(@PathVariable Long id, @Valid @RequestBody GrowingRequests.Task request) { return CommonResponse.ok(tasks.create(id, request)); }
    @GetMapping("/tasks/{id}/execution") public CommonResponse execution(@PathVariable Long id) { return CommonResponse.ok(tasks.execution(id)); }
    @PostMapping("/tasks/{id}/complete") public CommonResponse complete(@PathVariable Long id, @Valid @RequestBody GrowingRequests.Execution request) { return CommonResponse.ok(tasks.complete(id, request)); }
    @PostMapping("/tasks/{id}/cancel") public CommonResponse cancel(@PathVariable Long id, @Valid @RequestBody GrowingRequests.Reason request) { return CommonResponse.ok(tasks.cancel(id, request)); }
}
