package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.accounts.CurrentProfile;
import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * "Continuar viendo" del perfil en uso. El perfil sale de la sesión ({@link CurrentProfile}), no de
 * la URL; el servicio vuelve a verificar cuenta, perfil activo y suscripción con ViewingAccess.
 */
@Controller
@RequestMapping("/viewing/continue-watching")
public class ContinueWatchingController {
    private final PlaybackService playback;
    private final CurrentProfile currentProfile;

    public ContinueWatchingController(PlaybackService playback, CurrentProfile currentProfile) {
        this.playback = playback;
        this.currentProfile = currentProfile;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("items", playback.continueWatching(currentProfile.require().profileId()));
        return "viewing/continue-watching";
    }

    /**
     * Sección "Continuar viendo" de la página de inicio, que la carga con htmx al abrirse. Así el
     * módulo shared no depende de viewing, y si MongoDB no responde solo falta esta sección: la
     * página de inicio se sigue viendo.
     *
     * <p>A diferencia de la página completa, no redirige: sin perfil elegido, sin suscripción
     * vigente o sin nada pendiente, responde vacío y la página de inicio queda como estaba.</p>
     */
    @GetMapping("/home-section")
    public String homeSection(Model model) {
        List<PlaybackService.ContinueItem> items = currentProfile.current()
                .map(profile -> {
                    try {
                        return playback.continueWatching(profile.profileId());
                    } catch (AccessDeniedException noSubscription) {
                        return List.<PlaybackService.ContinueItem>of();
                    }
                })
                .orElse(List.of());
        model.addAttribute("items", items);
        return "viewing/fragments/continue-watching :: home-section";
    }

    @PostMapping("/{sessionId}")
    public String resume(@PathVariable String sessionId, RedirectAttributes redirect) {
        try {
            redirect.addAttribute("nextSession",
                    playback.resume(currentProfile.require().profileId(), sessionId).id());
            return "redirect:/viewing/player/{nextSession}";
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
            return "redirect:/viewing/continue-watching";
        }
    }
}
