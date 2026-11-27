package com.cineconecta.platform.catalog.web;

import com.cineconecta.platform.catalog.ViewerContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SOLO PARA DESARROLLO, mientras no exista la integración con cuentas: devuelve una clasificación
 * fija. Es opt-in a propósito (si la propiedad no está, el bean no existe y la aplicación no arranca
 * hasta que haya una implementación real), para que nunca llegue por accidente a producción.
 *
 * Activar en application-local.properties:
 *   cineconecta.catalog.dev-viewer.enabled=true
 *   cineconecta.catalog.dev-viewer.max-age-rating=12
 *
 * La implementación real (SessionViewerContext) leerá el profileId del token autenticado y pedirá
 * la clasificación máxima a la API pública del módulo de cuentas.
 */
@Component
@ConditionalOnProperty(name = "cineconecta.catalog.dev-viewer.enabled", havingValue = "true")
public class DevViewerContext implements ViewerContext {

    private final int maxAgeRating;

    public DevViewerContext(@Value("${cineconecta.catalog.dev-viewer.max-age-rating:0}") int maxAgeRating) {
        this.maxAgeRating = maxAgeRating;
    }

    @Override
    public int maxAgeRating() {
        return maxAgeRating;
    }
}
