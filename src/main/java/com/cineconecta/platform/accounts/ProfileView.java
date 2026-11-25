package com.cineconecta.platform.accounts;

/**
 * Proyección pública de un perfil de visualización perteneciente a una cuenta.
 */
public record ProfileView(
        int profileId,
        int accountId,
        String name,
        int maxAgeRating,
        boolean active
) {
}
