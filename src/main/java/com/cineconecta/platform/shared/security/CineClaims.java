package com.cineconecta.platform.shared.security;

/**
 * Nombres de los claims propios que usa la aplicación dentro del JWT.
 */
public final class CineClaims {

    /** Tipo de token: {@code customer} o {@code admin} (ver {@link TokenType}). */
    public static final String TYPE = "typ";

    /** Identificador de la cuenta de cliente (PostgreSQL, {@code ACCOUNTS.accountId}). */
    public static final String ACCOUNT_ID = "accountId";

    /** Perfil seleccionado (PostgreSQL, {@code PROFILES.profileId}); solo existe tras elegir perfil. */
    public static final String PROFILE_ID = "profileId";

    private CineClaims() {
    }
}
