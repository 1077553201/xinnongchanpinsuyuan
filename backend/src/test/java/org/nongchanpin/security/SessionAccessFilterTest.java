package org.nongchanpin.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.nongchanpin.model.entity.AccountEntity;
import org.nongchanpin.service.AccountService;
import org.springframework.mock.web.*;

class SessionAccessFilterTest {
    private final AccountService accounts = mock(AccountService.class);
    private final SessionAccessFilter filter = new SessionAccessFilter(accounts, new ObjectMapper());
    private MockHttpServletRequest request(String method, String path, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (role != null) {
            AccountEntity account = new AccountEntity(); account.setId(1L); account.setPasswordHash("stamp");
            Actor actor = new Actor(); actor.setId(1L); actor.setRole(role); actor.setOrganizationId(1L);
            when(accounts.get(1L)).thenReturn(account); when(accounts.actor(account)).thenReturn(actor);
            request.getSession().setAttribute("accountId", 1L);
            request.getSession().setAttribute("credentialStamp", "stamp");
            request.getSession().setAttribute("csrf", "expected");
        }
        return request;
    }
    @Test void rejectsAnonymousBusinessAccessButAllowsPublicQuery() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/production/plots", null), response, (req, res) -> fail("must reject"));
        assertEquals(401, response.getStatus());
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request("GET", "/api/public/trace/ABC", null), new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertTrue(called.get());
    }
    @Test void rejectsViewerMutationAndManagerLegacyAccess() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/production/plots", "VIEWER");
        request.addHeader("X-Requested-With", "XMLHttpRequest"); request.addHeader("X-CSRF-Token", "expected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("must reject")); assertEquals(403, response.getStatus());
        response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/iot/readings", "MANAGER"), response, (req, res) -> fail("must reject"));
        assertEquals(403, response.getStatus());
        assertThrows(AccessException.class, AccessContext::require);
    }
    @Test void requiresCsrfAndClearsIdentityAfterSuccess() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/production/plots", "MANAGER");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("must reject")); assertEquals(403, response.getStatus());
        request.addHeader("X-CSRF-Token", "expected");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> assertEquals("MANAGER", AccessContext.require().getRole()));
        assertThrows(AccessException.class, AccessContext::require);
    }
    @Test void revokesSessionWhenPasswordChanges() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/production/plots", "ADMIN");
        request.getSession().setAttribute("credentialStamp", "previous-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("must reject"));
        assertEquals(401, response.getStatus());
    }
    @Test void hashesWithRandomSaltAndRejectsWrongOrLegacyPasswords() {
        String first = Passwords.hash("a-long-test-password");
        assertNotEquals(first, Passwords.hash("a-long-test-password"));
        assertTrue(Passwords.matches("a-long-test-password", first));
        assertFalse(Passwords.matches("wrong", first));
        assertFalse(Passwords.matches("wrong", "plaintext"));
    }
    @Test void viewerCanUsePersonalChatWithCsrfButCannotConfirmBusiness() throws Exception {
        for(String path:new String[]{"/api/production/chat/conversations","/api/production/chat/conversations/7/messages"}) {
            MockHttpServletRequest request=request("POST",path,"VIEWER");request.addHeader("X-Requested-With","XMLHttpRequest");request.addHeader("X-CSRF-Token","expected");AtomicBoolean called=new AtomicBoolean();filter.doFilter(request,new MockHttpServletResponse(),(req,res)->called.set(true));assertTrue(called.get());
        }
        MockHttpServletRequest request=request("POST","/api/production/agent/reports/7/confirm","VIEWER");request.addHeader("X-Requested-With","XMLHttpRequest");request.addHeader("X-CSRF-Token","expected");MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->fail("business write must remain forbidden"));assertEquals(403,response.getStatus());
    }
    @Test void chatStillRequiresCsrf() throws Exception {MockHttpServletRequest request=request("POST","/api/production/chat/conversations/7/messages","VIEWER");request.addHeader("X-Requested-With","XMLHttpRequest");MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->fail("must reject"));assertEquals(403,response.getStatus());}
}
