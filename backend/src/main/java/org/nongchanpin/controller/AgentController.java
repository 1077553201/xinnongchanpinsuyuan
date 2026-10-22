package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.AgentRequests;
import org.nongchanpin.service.AgentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agent;
    @GetMapping("/status") public CommonResponse status(){return CommonResponse.ok(agent.status());}
    @GetMapping("/context") public CommonResponse context(@RequestParam(defaultValue="")String search){return CommonResponse.ok(agent.context(search));}
    @GetMapping("/reports") public CommonResponse list(@RequestParam(defaultValue="1")int page){return CommonResponse.ok(agent.list(page));}
    @GetMapping("/reports/{id}") public CommonResponse detail(@PathVariable Long id){return CommonResponse.ok(agent.detail(id));}
    @PostMapping("/reports") public CommonResponse create(@Valid @RequestBody AgentRequests.Create request){return CommonResponse.ok(agent.create(request));}
    @PostMapping("/reports/{id}/revise") public CommonResponse revise(@PathVariable Long id,@Valid @RequestBody AgentRequests.Revise request){return CommonResponse.ok(agent.revise(id,request));}
    @PostMapping("/reports/{id}/confirm") public CommonResponse confirm(@PathVariable Long id,@Valid @RequestBody AgentRequests.Confirm request){return CommonResponse.ok(agent.confirm(id,request));}
    @PostMapping("/reports/{id}/confirmations/{confirmation}/resume") public CommonResponse resume(@PathVariable Long id,@PathVariable Long confirmation){return CommonResponse.ok(agent.resume(id,confirmation));}
}
