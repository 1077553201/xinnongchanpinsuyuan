package org.nongchanpin.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.AccountMapper;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.entity.AccountEntity;
import org.nongchanpin.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountMapper accounts;
    private final ProductionMapper production;
    private final AuditService audit;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final String dummyHash = Passwords.hash("dummy-password-for-timing-only");

    @Data public static class Login {
        @NotBlank @Size(max = 64) private String username;
        @NotBlank @Size(max = 128) private String password;
    }
    @Data public static class Create {
        @NotNull @Positive private Long organizationId;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") private String username;
        @NotBlank @Size(min = 12, max = 128) private String password;
        @NotBlank @Size(max = 64) private String displayName;
        @NotBlank @Pattern(regexp = "ADMIN|MANAGER|VIEWER") private String role;
    }
    @Data public static class ChangePassword {
        @NotBlank @Size(max = 128) private String currentPassword;
        @NotBlank @Size(min = 12, max = 128) private String newPassword;
    }
    private static class Attempts { int failures; long until; }

    public AccountEntity authenticate(Login request, String address) {
        if (attempts.size() > 10000) attempts.entrySet().removeIf(entry -> entry.getValue().until < Instant.now().getEpochSecond());
        if (attempts.size() > 10000) throw new AccessException(429, "登录繁忙，请稍后再试。");
        Attempts state = attempts.computeIfAbsent(address, key -> new Attempts());
        synchronized (state) {
            long now = Instant.now().getEpochSecond();
            if (state.until < now) { state.failures = 0; state.until = now + 900; }
            if (state.failures >= 5) throw new AccessException(429, "尝试次数过多，请十五分钟后再试。");
            AccountEntity account = accounts.byUsername(request.getUsername().trim());
            boolean matches = Passwords.matches(request.getPassword(), account == null ? dummyHash : account.getPasswordHash());
            if (!matches || account == null || !"ACTIVE".equals(account.getStatus()) || actor(account) == null) {
                state.failures++;
                throw new AccessException(401, "账号或密码错误，或账号已停用。");
            }
            state.failures = 0;
            accounts.login(account.getId());
            return account;
        }
    }
    public AccountEntity get(Long id) { return accounts.byId(id); }
    public Actor actor(AccountEntity account) {
        if (account == null || !"ACTIVE".equals(account.getStatus()) || account.getOrganizationId() == null
                || production.activeOrganization(account.getOrganizationId()) != 1) return null;
        List<String> roles = accounts.roles(account.getId());
        String role = roles.contains("ADMIN") ? "ADMIN" : roles.contains("MANAGER") ? "MANAGER" : roles.contains("VIEWER") ? "VIEWER" : null;
        if (role == null) return null;
        Actor actor = new Actor();
        actor.setId(account.getId()); actor.setOrganizationId(account.getOrganizationId()); actor.setUsername(account.getUsername());
        actor.setDisplayName(account.getDisplayName()); actor.setRole(role);
        return actor;
    }
    public List<Map<String, Object>> list() {
        AccessContext.admin();
        return accounts.all().stream().map(account -> Map.<String, Object>of("id", account.getId(), "username", account.getUsername(),
                "displayName", account.getDisplayName(), "organizationId", account.getOrganizationId() == null ? 0L : account.getOrganizationId(),
                "status", account.getStatus(), "roles", accounts.roles(account.getId()))).collect(Collectors.toList());
    }
    @Transactional public Long create(Create request) {
        AccessContext.admin();
        if (production.activeOrganization(request.getOrganizationId()) != 1) throw new IllegalArgumentException("机构不存在或已停用。");
        AccountEntity account = new AccountEntity();
        account.setOrganizationId(request.getOrganizationId()); account.setUsername(request.getUsername());
        account.setDisplayName(request.getDisplayName().trim()); account.setPasswordHash(Passwords.hash(request.getPassword()));
        accounts.insert(account);
        if (accounts.grant(account.getId(), request.getRole()) != 1) throw new IllegalArgumentException("角色尚未配置。");
        audit.record("ACCOUNT_CREATE", "ACCOUNT", account.getId(), request.getRole());
        return account.getId();
    }
    @Transactional public void disable(Long id) {
        AccessContext.admin();
        if (id.equals(AccessContext.require().getId())) throw new IllegalArgumentException("不能停用当前登录账号。");
        if (accounts.byId(id) == null) throw new IllegalArgumentException("账号不存在。");
        accounts.disable(id);
        audit.record("ACCOUNT_DISABLE", "ACCOUNT", id, "停用账号");
    }
    @Transactional public void changePassword(ChangePassword request) {
        Actor actor = AccessContext.require();
        if (!Passwords.matches(request.getCurrentPassword(), accounts.byId(actor.getId()).getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确。");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) throw new IllegalArgumentException("新密码须与当前密码不同。");
        accounts.password(actor.getId(), Passwords.hash(request.getNewPassword()));
        audit.record("PASSWORD_CHANGE", "ACCOUNT", actor.getId(), "修改本人密码");
    }
}
