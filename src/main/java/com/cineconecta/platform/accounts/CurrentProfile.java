package com.cineconecta.platform.accounts;

import java.util.Optional;

/**
 * API pública: perfil en uso en la petición actual (el que el cliente eligió en /profiles).
 *
 * <p>El perfil se toma del JWT, pero siempre se vuelve a verificar en PostgreSQL: solo se devuelve
 * si pertenece a la cuenta autenticada y sigue activo. Así ningún módulo necesita recibir un
 * {@code profileId} por la URL ni confiar en él.</p>
 */
public interface CurrentProfile {

    /** Perfil en uso verificado, o vacío si no hay ninguno elegido (o ya no es válido). */
    Optional<ProfileView> current();

    /**
     * Perfil en uso verificado. Si no hay, lanza una excepción que la aplicación convierte en una
     * redirección a elegir perfil (y luego vuelve a la página pedida).
     */
    ProfileView require();
}
