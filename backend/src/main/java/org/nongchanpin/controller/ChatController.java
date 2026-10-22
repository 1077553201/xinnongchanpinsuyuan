package org.nongchanpin.controller;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.ChatRequests;
import org.nongchanpin.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chat;
    @GetMapping("/conversations") public CommonResponse list(@RequestParam(defaultValue="1")int page){return CommonResponse.ok(chat.list(page));}
    @PostMapping("/conversations") public CommonResponse create(@Valid @RequestBody ChatRequests.Create request){return CommonResponse.ok(chat.create(request));}
    @GetMapping("/conversations/{id}") public CommonResponse detail(@PathVariable Long id,@RequestParam(required=false)Long before){return CommonResponse.ok(chat.detail(id,before));}
    @PostMapping("/conversations/{id}/messages") public CommonResponse send(@PathVariable Long id,@Valid @RequestBody ChatRequests.Message request){return CommonResponse.ok(chat.send(id,request));}
}
