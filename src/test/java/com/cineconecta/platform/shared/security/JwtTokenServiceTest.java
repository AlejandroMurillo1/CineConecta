package com.cineconecta.platform.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-bytes-0123456789";
    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");

    private static JwtProperties props(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(60), "cineconecta", false);
    }

    private static JwtTokenService serviceAt(Instant instant) {
        return new JwtTokenService(props(SECRET), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuedCustomerTokenDecodesWithItsClaims() {
        JwtTokenService service = serviceAt(NOW);

        IssuedToken token = service.issue(TokenType.CUSTOMER, "42",
                Map.of(CineClaims.ACCOUNT_ID, 42, CineClaims.PROFILE_ID, 7));
        Jwt jwt = service.decoderFor(TokenType.CUSTOMER).decode(token.value());

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(CineClaims.TYPE)).isEqualTo("customer");
        assertThat(((Number) jwt.getClaim(CineClaims.ACCOUNT_ID)).intValue()).isEqualTo(42);
        assertThat(((Number) jwt.getClaim(CineClaims.PROFILE_ID)).intValue()).isEqualTo(7);
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
    }

    @Test
    void customerTokenIsRejectedByAdminDecoder() {
        JwtTokenService service = serviceAt(NOW);
        IssuedToken token = service.issue(TokenType.CUSTOMER, "42", Map.of());

        assertThatThrownBy(() -> service.decoderFor(TokenType.ADMIN).decode(token.value()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void adminTokenIsRejectedByCustomerDecoder() {
        JwtTokenService service = serviceAt(NOW);
        IssuedToken token = service.issue(TokenType.ADMIN, "admin@cineconecta.local", Map.of());

        assertThatThrownBy(() -> service.decoderFor(TokenType.CUSTOMER).decode(token.value()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        IssuedToken token = serviceAt(NOW).issue(TokenType.CUSTOMER, "42", Map.of());
        JwtTokenService later = serviceAt(NOW.plus(Duration.ofMinutes(61)));

        assertThatThrownBy(() -> later.decoderFor(TokenType.CUSTOMER).decode(token.value()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtTokenService other = new JwtTokenService(
                props("another-secret-with-at-least-32-bytes-987654"), Clock.fixed(NOW, ZoneOffset.UTC));
        IssuedToken forged = other.issue(TokenType.CUSTOMER, "42", Map.of());

        assertThatThrownBy(() -> serviceAt(NOW).decoderFor(TokenType.CUSTOMER).decode(forged.value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtTokenService service = serviceAt(NOW);
        String[] parts = service.issue(TokenType.CUSTOMER, "42", Map.of()).value().split("\\.");
        String otherPayload = service.issue(TokenType.CUSTOMER, "99", Map.of()).value().split("\\.")[1];
        String tampered = parts[0] + "." + otherPayload + "." + parts[2];

        assertThatThrownBy(() -> service.decoderFor(TokenType.CUSTOMER).decode(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void reservedClaimsCannotBeOverridden() {
        JwtTokenService service = serviceAt(NOW);

        assertThatThrownBy(() -> service.issue(TokenType.CUSTOMER, "42", Map.of(CineClaims.TYPE, "admin")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issue(TokenType.CUSTOMER, "42", Map.of("exp", 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> props("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
