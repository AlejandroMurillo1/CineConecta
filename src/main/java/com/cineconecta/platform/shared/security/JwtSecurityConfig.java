package com.cineconecta.platform.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Publica la infraestructura JWT compartida. No define ninguna cadena de seguridad: cada módulo
 * (accounts para clientes, backoffice para administradores) declara la suya.
 *
 * <p>A propósito no se publica un bean {@code JwtDecoder}: con uno solo, Spring Boot lo usaría en
 * cualquier cadena y un token de administrador podría aceptarse como de cliente. Cada cadena pide
 * el suyo con {@link JwtTokenService#decoderFor(TokenType)}.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSecurityConfig {

    @Bean
    public JwtTokenService jwtTokenService(JwtProperties properties, Clock clock) {
        return new JwtTokenService(properties, clock);
    }

    @Bean
    public AuthCookies authCookies(JwtProperties properties, Clock clock) {
        return new AuthCookies(properties, clock);
    }
}
