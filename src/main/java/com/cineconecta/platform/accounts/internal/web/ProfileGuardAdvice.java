package com.cineconecta.platform.accounts.internal.web;

import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.security.CurrentProfileResolver;
import com.cineconecta.platform.accounts.internal.security.ProfileSelectionRequiredException;
import com.cineconecta.platform.accounts.internal.security.SubscriptionRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Aplica a todos los controladores:
 * <ul>
 *   <li>{@code currentProfile}: el perfil en uso (verificado en PostgreSQL) para la navegación;
 *       {@code null} si no hay.</li>
 *   <li>Sin perfil seleccionado: redirige a elegir perfil y luego vuelve a la página pedida.</li>
 *   <li>Sin suscripción vigente: redirige a los planes.</li>
 * </ul>
 * En peticiones de htmx se responde con la cabecera {@code HX-Redirect} para que el navegador
 * cambie de página en lugar de pintar la redirección dentro de un fragmento.
 */
@ControllerAdvice
public class ProfileGuardAdvice {

    /**
     * Opcional: las pruebas {@code @WebMvcTest} de otros módulos cargan todos los
     * {@code @ControllerAdvice} pero no los servicios de accounts; ahí simplemente no hay perfil.
     */
    private final ObjectProvider<CurrentProfileResolver> currentProfile;

    public ProfileGuardAdvice(ObjectProvider<CurrentProfileResolver> currentProfile) {
        this.currentProfile = currentProfile;
    }

    @ModelAttribute("currentProfile")
    public ProfileView currentProfile() {
        CurrentProfileResolver resolver = currentProfile.getIfAvailable();
        return resolver == null ? null : resolver.current().orElse(null);
    }

    @ExceptionHandler(ProfileSelectionRequiredException.class)
    public ModelAndView chooseProfile(HttpServletRequest request, HttpServletResponse response) {
        UriComponentsBuilder target = UriComponentsBuilder.fromPath(request.getContextPath() + "/profiles");
        if ("GET".equals(request.getMethod()) && !isHtmx(request)) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            target.queryParam("next", request.getQueryString() == null ? path : path + "?" + request.getQueryString());
        }
        return redirect(target.encode().build().toUriString(), request, response);
    }

    @ExceptionHandler(SubscriptionRequiredException.class)
    public ModelAndView choosePlan(HttpServletRequest request, HttpServletResponse response) {
        return redirect(request.getContextPath() + "/billing/plans", request, response);
    }

    private static ModelAndView redirect(String url, HttpServletRequest request, HttpServletResponse response) {
        if (isHtmx(request)) {
            response.setHeader("HX-Redirect", url);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null; // respuesta ya resuelta
        }
        RedirectView view = new RedirectView(url);
        view.setContextRelative(false);
        return new ModelAndView(view);
    }

    private static boolean isHtmx(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
