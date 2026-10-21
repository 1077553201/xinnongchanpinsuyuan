package org.nongchanpin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.servlet.*;
import javax.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.entity.AccountEntity;
import org.nongchanpin.service.AccountService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(10)
@RequiredArgsConstructor
public class SessionAccessFilter extends OncePerRequestFilter {
    private final AccountService accounts;
    private final ObjectMapper json;
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        boolean safe = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        if (!path.startsWith("/api/")) { chain.doFilter(request, response); return; }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try {
            if (safe && path.startsWith("/api/public/trace/")) { chain.doFilter(request, response); return; }
            if (!safe && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                throw new AccessException(403, "请求校验失败，请刷新页面后重试。");
            }
            if ("/api/auth/login".equals(path) && "POST".equals(request.getMethod())) { chain.doFilter(request, response); return; }
            HttpSession session = request.getSession(false);
            Long id = session == null ? null : (Long) session.getAttribute("accountId");
            AccountEntity account = id == null ? null : accounts.get(id);
            Actor actor = accounts.actor(account);
            if (actor == null || !account.getPasswordHash().equals(session.getAttribute("credentialStamp"))) {
                if (session != null) session.invalidate();
                throw new AccessException(401, "登录已失效，请重新登录。");
            }
            AccessContext.set(actor);
            if (!safe) {
                String expected = (String) session.getAttribute("csrf");
                String supplied = request.getHeader("X-CSRF-Token");
                if (expected == null || supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                    throw new AccessException(403, "请求校验已失效，请刷新页面后重试。");
                }
                if (!path.startsWith("/api/auth/")) AccessContext.write();
            }
            if (!path.startsWith("/api/auth/") && !path.startsWith("/api/production/")
                    && !path.equals("/api/products") && !path.matches("/api/products/[0-9]+")) AccessContext.admin();
            chain.doFilter(request, response);
        } catch (AccessException exception) {
            response.setStatus(exception.getStatus());
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getWriter(), new CommonResponse(String.valueOf(exception.getStatus()), exception.getMessage(), null));
        } finally { AccessContext.clear(); }
    }
}
