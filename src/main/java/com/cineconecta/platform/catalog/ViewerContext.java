package com.cineconecta.platform.catalog;

import java.util.OptionalInt;

/**
 * Public catalog port for the authenticated viewer's age ceiling.
 * The accounts module supplies the production adapter using its own profile API.
 */
public interface ViewerContext {

    /**
     * Clasificación máxima del perfil en uso. Si no hay perfil elegido o la cuenta no tiene
     * suscripción vigente, lanza una excepción que la aplicación convierte en una redirección.
     */
    int maxAgeRating();

    /**
     * Igual que {@link #maxAgeRating()}, pero sin redirigir: vacío si el perfil en uso no puede ver
     * el catálogo. Para secciones incrustadas en otras páginas (p. ej. la de inicio).
     */
    OptionalInt allowedMaxAgeRating();
}
