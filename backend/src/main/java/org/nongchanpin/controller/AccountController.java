package org.nongchanpin.controller;

import java.util.Map;
import java.util.UUID;
import javax.servlet.http.*;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.entity.AccountEntity;
import org.nongchanpin.security.AccessContext;
import org.nongchanpin.service.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accounts;
    @PostMapping("/login") public CommonResponse login(@Valid @RequestBody AccountService.Login body, HttpServletRequest request) {
        AccountEntity account = accounts.authenticate(body, request.getRemoteAddr());
        HttpSession previous = request.getSession(false);
        if (previous != null) previous.invalidate();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(1800);
        session.setAttribute("accountId", account.getId());
        session.setAttribute("credentialStamp", account.getPasswordHash());
        session.setAttribute("csrf", UUID.randomUUID().toString());
        return CommonResponse.ok(Map.of("user", accounts.actor(account), "csrfToken", session.getAttribute("csrf")));
    }
    @GetMapping("/me") public CommonResponse me(HttpServletRequest request) {
        return CommonResponse.ok(Map.of("user", AccessContext.require(), "csrfToken", request.getSession(false).getAttribute("csrf")));
    }
    @PostMapping("/logout") public CommonResponse logout(HttpServletRequest request) {
        request.getSession(false).invalidate(); return CommonResponse.ok(null);
    }
    @GetMapping("/accounts") public CommonResponse list() { return CommonResponse.ok(accounts.list()); }
    @PostMapping("/accounts") public CommonResponse create(@Valid @RequestBody AccountService.Create request) { return CommonResponse.ok(accounts.create(request)); }
    @PostMapping("/accounts/{id}/disable") public CommonResponse disable(@PathVariable Long id) { accounts.disable(id); return CommonResponse.ok(null); }
    @PostMapping("/password") public CommonResponse password(@Valid @RequestBody AccountService.ChangePassword body, HttpServletRequest request) {
        accounts.changePassword(body); request.getSession(false).invalidate(); return CommonResponse.ok(null);
    }
}
