package com.cineconecta.platform.backoffice.internal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de seguridad de administradores, aislada en {@code /admin/**}.
 *
 * <ul>
 *   <li>{@code @Order(1)}: se evalúa antes que cualquier cadena de clientes. Las solicitudes que no
 *       empiezan por {@code /admin} no pasan por esta cadena.</li>
 *   <li>Autenticación con formulario y sesión HTTP, contra {@code admin_users} (MongoDB) con BCrypt.
 *       Cuando exista la infraestructura de JWT compartida (módulo accounts/shared), esta cadena
 *       puede migrarse a un token en la cookie {@code CINE_ADMIN} sin cambiar las pantallas.</li>
 *   <li>El codificador BCrypt y el proveedor de autenticación son locales a esta cadena: no se
 *       publican como beans para no afectar la autenticación de clientes.</li>
 *   <li>CSRF activo: los formularios de Thymeleaf con {@code th:action} incluyen el token solos.</li>
 *   <li>Toda ruta bajo {@code /admin/**} exige el rol ADMIN, salvo la página de inicio de sesión.
 *       Así el servidor verifica la autorización en cada operación administrativa.</li>
 * </ul>
 */
@Configuration
public class AdminSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, AdminUserStore adminUsers) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new AdminUserDetailsService(adminUsers));
        provider.setPasswordEncoder(new BCryptPasswordEncoder());

        return http
                .securityMatcher("/admin/**")
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        // La segunda ruta es el aviso de MongoDB no disponible (reenvío interno al fallar el login).
                        .requestMatchers("/admin/login", AdminLoginFailureHandler.UNAVAILABLE_PATH).permitAll()
                        .anyRequest().hasRole(AdminPrincipal.ROLE))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        // Vuelve a la página protegida que se pidió; si no había ninguna, al panel.
                        .defaultSuccessUrl("/admin", false)
                        // Credenciales incorrectas: /admin/login?error. MongoDB caído: 503 con aviso.
                        .failureHandler(new AdminLoginFailureHandler()))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .build();
    }
}
