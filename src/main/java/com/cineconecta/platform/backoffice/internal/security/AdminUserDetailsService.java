package com.cineconecta.platform.backoffice.internal.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Locale;

/**
 * Carga administradores para el inicio de sesión de {@code /admin/**}.
 *
 * <p>No se registra como bean a propósito: así no se convierte en el {@link UserDetailsService}
 * global de la aplicación y no interfiere con la autenticación de clientes (módulo accounts).
 * Solo lo usa la cadena de seguridad de administradores.</p>
 */
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserStore store;

    public AdminUserDetailsService(AdminUserStore store) {
        this.store = store;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return store.findByEmail(normalizeEmail(username))
                .map(AdminPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Administrador no encontrado"));
    }

    /** Los correos se guardan en minúsculas (lo exige el validador de admin_users). */
    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
