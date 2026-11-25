package com.cineconecta.platform.shared.config;

import com.cineconecta.platform.shared.CurrentAccountProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
import java.util.OptionalInt;

/**
 * Configuración compartida base (reloj UTC y respaldos temporales mientras el módulo accounts/security entra en línea).
 */
@Configuration
public class SharedConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(CurrentAccountProvider.class)
    public CurrentAccountProvider fallbackCurrentAccountProvider(
            @Value("${cineconecta.dev.default-account-id:0}") int defaultAccountId) {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
                Object claim = jwt.getClaim("accountId");
                if (claim instanceof Number n) {
                    return OptionalInt.of(n.intValue());
                }
                if (claim instanceof String s && !s.isBlank()) {
                    return OptionalInt.of(Integer.parseInt(s.trim()));
                }
            }
            return defaultAccountId > 0 ? OptionalInt.of(defaultAccountId) : OptionalInt.empty();
        };
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/billing/**", "/actuator/health", "/css/**", "/js/**", "/vendor/**", "/")
                        .permitAll()
                        .anyRequest().permitAll())
                .build();
    }
}
