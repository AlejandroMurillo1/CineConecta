package com.cineconecta.platform.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Escribe y borra las cookies que transportan el JWT.
 *
 * <p>Atributos: {@code HttpOnly} (JavaScript no puede leer el token), {@code SameSite=Lax},
 * {@code Path=/} y {@code Secure} si {@code app.jwt.cookie-secure=true}. La duración de la cookie
 * coincide con la expiración del token.</p>
 */
public class AuthCookies {

    private final JwtProperties properties;
    private final Clock clock;

    public AuthCookies(JwtProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Envía el token en su cookie (inicio de sesión o reemisión al elegir perfil). */
    public void write(HttpServletResponse response, IssuedToken token) {
        Duration maxAge = Duration.between(clock.instant(), token.expiresAt());
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        addCookie(response, token.type(), token.value(), maxAge);
    }

    /** Elimina la cookie del tipo indicado (cierre de sesión o token inválido). */
    public void clear(HttpServletResponse response, TokenType type) {
        addCookie(response, type, "", Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, TokenType type, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(type.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
