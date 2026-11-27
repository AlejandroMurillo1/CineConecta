package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.accounts.internal.persistence.AccountRepository;
import com.cineconecta.platform.accounts.internal.service.AccountService;
import com.cineconecta.platform.shared.security.AuthCookies;
import com.cineconecta.platform.shared.security.CookieBearerTokenResolver;
import com.cineconecta.platform.shared.security.JwtTokenService;
import com.cineconecta.platform.shared.security.TokenType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.time.Clock;
import java.util.List;

/**
 * Cadena de seguridad de clientes: atiende todo lo que no es {@code /admin/**} (esa ruta la toma
 * antes la cadena de backoffice, {@code @Order(1)}).
 *
 * <ul>
 *   <li>Sin sesión HTTP: la identidad viaja en el JWT de la cookie {@code CINE_TOKEN}, validado
 *       con el decodificador de clientes (firma, emisor, expiración y {@code typ=customer}).</li>
 *   <li>CSRF activo con el token en una cookie {@code HttpOnly} (no hay sesión donde guardarlo).
 *       Thymeleaf lo inserta en los formularios y app.js lo envía en las peticiones de htmx.</li>
 *   <li>Rutas públicas: inicio, login, registro, listado de planes, errores y recursos estáticos.
 *       El resto exige el rol CUSTOMER.</li>
 * </ul>
 */
@Configuration
public class CustomerSecurityConfig {

    static final String ROLE = "CUSTOMER";

    @Bean
    @Order(2)
    public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http,
                                                           JwtTokenService tokens,
                                                           AuthCookies cookies) throws Exception {
        CustomerAuthenticationEntryPoint entryPoint = new CustomerAuthenticationEntryPoint(cookies);

        JwtAuthenticationConverter toAuthentication = new JwtAuthenticationConverter();
        toAuthentication.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));

        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/error",
                                "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico",
                                "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/billing/plans").permitAll()
                        .anyRequest().hasRole(ROLE))
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(new CookieBearerTokenResolver(TokenType.CUSTOMER))
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt
                                .decoder(tokens.decoderFor(TokenType.CUSTOMER))
                                .jwtAuthenticationConverter(toAuthentication)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler((request, response, auth) -> cookies.clear(response, TokenType.CUSTOMER))
                        .logoutSuccessUrl("/login?logout"))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * BCrypt local al módulo (no se publica un {@code PasswordEncoder} global para no interferir
     * con la autenticación de administradores, que usa el suyo).
     */
    @Bean
    public AccountService accountService(AccountRepository accounts, Clock clock) {
        return new AccountService(accounts, new BCryptPasswordEncoder(), clock);
    }
}
