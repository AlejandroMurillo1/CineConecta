package com.cineconecta.platform.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.Objects;

/**
 * Lee el JWT desde la cookie {@code HttpOnly} del tipo indicado, en lugar de la cabecera
 * {@code Authorization}. Con Thymeleaf las páginas se piden por navegación normal, así que el
 * navegador debe enviar el token solo.
 *
 * <p>Uso en una cadena de seguridad:</p>
 * <pre>{@code
 * http.oauth2ResourceServer(rs -> rs
 *         .bearerTokenResolver(new CookieBearerTokenResolver(TokenType.CUSTOMER))
 *         .jwt(jwt -> jwt.decoder(tokens.decoderFor(TokenType.CUSTOMER))));
 * }</pre>
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final String cookieName;

    public CookieBearerTokenResolver(TokenType type) {
        this.cookieName = Objects.requireNonNull(type, "type").cookieName();
    }

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }
}
