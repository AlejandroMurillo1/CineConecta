package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.accounts.CurrentProfile;
import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import com.cineconecta.platform.viewing.internal.service.WatchedTitles;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Agrega a las pantallas del catálogo lo que el perfil en uso ya vio (atributo {@code watched}).
 *
 * <p>Se declara por nombre de paquete para que catalog no dependa de viewing (viewing ya depende
 * de catalog, y la dependencia inversa crearía un ciclo entre módulos). Si algo falla, el catálogo
 * se muestra igual, solo que sin las marcas de "visto".</p>
 */
@ControllerAdvice(basePackages = "com.cineconecta.platform.catalog.web")
public class WatchedTitlesAdvice {

    private static final Logger log = LoggerFactory.getLogger(WatchedTitlesAdvice.class);

    /**
     * Opcionales, como en ProfileGuardAdvice: las pruebas {@code @WebMvcTest} de otros módulos cargan
     * todos los {@code @ControllerAdvice} pero no estos servicios; ahí simplemente no hay marcas.
     */
    private final ObjectProvider<CurrentProfile> currentProfile;
    private final ObjectProvider<PlaybackService> playback;

    public WatchedTitlesAdvice(ObjectProvider<CurrentProfile> currentProfile, ObjectProvider<PlaybackService> playback) {
        this.currentProfile = currentProfile;
        this.playback = playback;
    }

    @ModelAttribute("watched")
    public WatchedTitles watched(HttpServletRequest request) {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/admin")) {
            return WatchedTitles.NONE;
        }
        CurrentProfile profiles = currentProfile.getIfAvailable();
        PlaybackService service = playback.getIfAvailable();
        if (profiles == null || service == null) {
            return WatchedTitles.NONE;
        }
        try {
            return profiles.current().map(service::watched).orElse(WatchedTitles.NONE);
        } catch (RuntimeException error) {
            log.warn("No se pudo consultar lo visto por el perfil; el catálogo se muestra sin marcas", error);
            return WatchedTitles.NONE;
        }
    }

    @ModelAttribute("resumableSessions")
    public java.util.Map<String, String> resumableSessions(HttpServletRequest request) {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/admin")) {
            return java.util.Map.of();
        }
        CurrentProfile profiles = currentProfile.getIfAvailable();
        PlaybackService service = playback.getIfAvailable();
        if (profiles == null || service == null) {
            return java.util.Map.of();
        }
        try {
            return profiles.current().map(service::resumableSessionIds).orElse(java.util.Map.of());
        } catch (RuntimeException error) {
            log.warn("No se pudo consultar sesiones reanudables del perfil", error);
            return java.util.Map.of();
        }
    }
}
