package com.cineconecta.platform.backoffice;

import com.cineconecta.platform.backoffice.internal.security.AdminLoginFailureHandler;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.ServerAddress;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminLoginFailureHandlerTest {

    private final AdminLoginFailureHandler handler = new AdminLoginFailureHandler();

    @Test
    void wrongCredentialsRedirectWithError() throws Exception {
        var response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest("POST", "/admin/login"), response,
                new BadCredentialsException("Credenciales incorrectas"));

        assertEquals("/admin/login?error", response.getRedirectedUrl());
    }

    @Test
    void mongoErrorTranslatedBySpringDataIsAlso503() throws Exception {
        var response = new MockHttpServletResponse();
        var cause = new DataAccessResourceFailureException("caída",
                new MongoSocketOpenException("Connection refused", new ServerAddress("localhost", 27017)));
        handler.onAuthenticationFailure(new MockHttpServletRequest("POST", "/admin/login"), response,
                new InternalAuthenticationServiceException("caída", cause));

        assertEquals(503, response.getStatus());
        assertEquals(AdminLoginFailureHandler.UNAVAILABLE_PATH, response.getForwardedUrl());
    }

    @Test
    void otherInternalErrorsAreNotReportedAsDatabaseDown() throws Exception {
        var response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest("POST", "/admin/login"), response,
                new InternalAuthenticationServiceException("fallo", new IllegalStateException("bug")));

        assertNull(response.getForwardedUrl());
        assertEquals("/admin/login?error", response.getRedirectedUrl());
    }
}
