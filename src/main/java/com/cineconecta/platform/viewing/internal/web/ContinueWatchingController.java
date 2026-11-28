package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.accounts.CurrentProfile;
import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
