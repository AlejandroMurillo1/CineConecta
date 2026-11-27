package com.cineconecta.platform.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Configuración del JWT ({@code app.jwt.*}).
 *
 * @param secret       clave HMAC (HS256); mínimo 32 bytes y distinta en cada entorno
 * @param ttl          vigencia del token (por defecto 60 minutos; no hay refresh tokens)
 * @param issuer       valor del claim {@code iss}, verificado al decodificar
 * @param cookieSecure si las cookies llevan el atributo {@code Secure} (activar con HTTPS)
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("60m") Duration ttl,
        @DefaultValue("cineconecta") String issuer,
        @DefaultValue("false") boolean cookieSecure
) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret debe tener al menos " + MIN_SECRET_BYTES + " bytes (variable JWT_SECRET)");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("app.jwt.ttl debe ser positivo");
        }
    }

    byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
