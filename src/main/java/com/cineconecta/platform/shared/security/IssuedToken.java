package com.cineconecta.platform.shared.security;

import java.time.Instant;

/**
 * Token firmado listo para enviarse en una cookie.
 *
 * @param type      tipo de token (define la cookie)
 * @param value     JWT compacto
 * @param expiresAt instante de expiración (claim {@code exp})
 */
public record IssuedToken(TokenType type, String value, Instant expiresAt) {
}
