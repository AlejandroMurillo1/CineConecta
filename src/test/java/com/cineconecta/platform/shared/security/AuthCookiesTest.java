package com.cineconecta.platform.shared.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookiesTest {

    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static AuthCookies cookies(boolean secure) {
        JwtProperties props = new JwtProperties(
                "test-secret-with-at-least-32-bytes-0123456789", Duration.ofMinutes(60), "cineconecta", secure);
        return new AuthCookies(props, CLOCK);
    }

    @Test
    void writesHttpOnlyLaxCookieThatLivesAsLongAsTheToken() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies(false).write(response, new IssuedToken(TokenType.CUSTOMER, "abc.def.ghi", NOW.plusSeconds(3600)));

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header)
                .startsWith("CINE_TOKEN=abc.def.ghi")
                .contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=3600")
                .doesNotContain("Secure");
    }

    @Test
    void addsSecureWhenConfigured() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies(true).write(response, new IssuedToken(TokenType.ADMIN, "t", NOW.plusSeconds(60)));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("CINE_ADMIN=t").contains("Secure");
    }

    @Test
    void clearExpiresTheCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies(false).clear(response, TokenType.CUSTOMER);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("CINE_TOKEN=;").contains("Max-Age=0");
    }

    @Test
    void resolverReadsOnlyTheCookieOfItsType() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("CINE_ADMIN", "admin-token"), new Cookie("CINE_TOKEN", "customer-token"));

        assertThat(new CookieBearerTokenResolver(TokenType.CUSTOMER).resolve(request)).isEqualTo("customer-token");
        assertThat(new CookieBearerTokenResolver(TokenType.ADMIN).resolve(request)).isEqualTo("admin-token");
    }

    @Test
    void resolverReturnsNullWithoutCookieOrWithBlankValue() {
        MockHttpServletRequest noCookies = new MockHttpServletRequest();
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie("CINE_TOKEN", ""));

        CookieBearerTokenResolver resolver = new CookieBearerTokenResolver(TokenType.CUSTOMER);
        assertThat(resolver.resolve(noCookies)).isNull();
        assertThat(resolver.resolve(blank)).isNull();
    }
}
