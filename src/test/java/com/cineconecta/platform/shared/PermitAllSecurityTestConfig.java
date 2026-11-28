package com.cineconecta.platform.shared;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Solo para pruebas de controladores ({@code @WebMvcTest}) que no prueban la seguridad: deja pasar
 * todas las peticiones, pero mantiene CSRF activo (las pruebas deben seguir usando {@code csrf()}).
 *
 * <p>Uso: {@code @Import(PermitAllSecurityTestConfig.class)}. Las reglas reales de clientes están en
 * accounts ({@code CustomerSecurityConfig}) y las de administradores en backoffice.</p>
 */
@TestConfiguration
public class PermitAllSecurityTestConfig {

    @Bean
    SecurityFilterChain permitAllTestSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
