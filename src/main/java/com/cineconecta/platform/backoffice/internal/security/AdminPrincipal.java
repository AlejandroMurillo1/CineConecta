package com.cineconecta.platform.backoffice.internal.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.Serial;
import java.util.List;

/**
 * Administrador autenticado. Se guarda en la sesión HTTP, por eso es serializable.
 * El nombre de usuario es el correo; {@link #getDisplayName()} es el nombre para mostrar.
 */
public class AdminPrincipal extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ROLE = "ADMIN";

    private final String adminId;
    private final String displayName;

    public AdminPrincipal(AdminAccount account) {
        super(account.email(), account.passwordHash(), List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
        this.adminId = account.id();
        this.displayName = account.name();
    }

    public String getAdminId() {
        return adminId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
