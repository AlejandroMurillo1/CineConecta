package com.cineconecta.platform.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Emite y valida los JWT de la aplicación (HS256 con las clases Nimbus que trae Spring Security).
 *
 * <p>Este servicio no conoce cuentas ni administradores: cada módulo decide el {@code subject} y los
 * claims adicionales, y configura su propia cadena de seguridad con {@link #decoderFor(TokenType)} y
 * {@link CookieBearerTokenResolver}.</p>
 *
 * <p>El JWT solo demuestra quién es el usuario. "Perfil activo" y "suscripción vigente" se vuelven
 * a verificar en la base relacional en cada operación que los necesite.</p>
 */
public class JwtTokenService {

    private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;
    private final JwtEncoder encoder;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.key = new SecretKeySpec(properties.secretBytes(), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /**
     * Firma un token nuevo.
     *
     * @param type         tipo de token (se guarda en el claim {@code typ})
     * @param subject      identificador del usuario (claim {@code sub})
     * @param extraClaims  claims adicionales; no pueden sobrescribir los reservados
     */
    public IssuedToken issue(TokenType type, String subject, Map<String, ?> extraClaims) {
        Objects.requireNonNull(type, "type");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject es obligatorio");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.ttl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(expiresAt);
        extraClaims.forEach((name, value) -> {
            if (isReserved(name)) {
                throw new IllegalArgumentException("El claim '" + name + "' es reservado");
            }
            claims.claim(name, value);
        });
        claims.claim(CineClaims.TYPE, type.claimValue());

        JwsHeader header = JwsHeader.with(ALGORITHM).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(type, token, expiresAt);
    }

    /**
     * Decodificador que acepta únicamente tokens de este tipo, firmados con la clave de la
     * aplicación, emitidos por este emisor y no expirados.
     */
    public JwtDecoder decoderFor(TokenType type) {
        Objects.requireNonNull(type, "type");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(ALGORITHM)
                .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<String>(CineClaims.TYPE, type.claimValue()::equals));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static boolean isReserved(String name) {
        return switch (name) {
            case "iss", "sub", "iat", "exp", "nbf", "aud", "jti", CineClaims.TYPE -> true;
            default -> false;
        };
    }
}
