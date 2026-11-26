package com.cineconecta.platform.backoffice.internal.security;

/**
 * Administrador leído de la colección {@code admin_users}.
 *
 * @param passwordHash hash BCrypt; nunca se guarda ni se maneja la contraseña en claro
 */
public record AdminAccount(String id, String name, String email, String passwordHash) {
}
