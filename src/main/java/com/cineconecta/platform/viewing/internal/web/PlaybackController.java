package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.accounts.CurrentProfile;
import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession;
import com.cineconecta.platform.viewing.internal.service.ViewingAccess;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Reproductor simulado del perfil en uso. El perfil sale de la sesión ({@link CurrentProfile}), no
 * de la URL; el servicio vuelve a verificar cuenta, perfil activo y suscripción con ViewingAccess.
 */
@Controller
@RequestMapping("/viewing/player")
public class PlaybackController {
    private final ViewingAccess access;
    private final PlaybackService playback;
    private final CurrentProfile currentProfile;

    public PlaybackController(ViewingAccess access, PlaybackService playback, CurrentProfile currentProfile) {
        this.access = access;
        this.playback = playback;
        this.currentProfile = currentProfile;
    }

    @GetMapping
    public String showStart(Model model) {
        access.requireEligibleProfile(currentProfile.require().profileId());
        return "viewing/player";
    }

    @PostMapping
    public String start(@RequestParam String contentId,
                        @RequestParam(required = false) String episodeId, RedirectAttributes redirect) {
        try {
            redirect.addAttribute("sessionId",
                    playback.start(currentProfile.require().profileId(), contentId, episodeId).id());
            return "redirect:/viewing/player/{sessionId}";
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
            return "redirect:/viewing/player";
        }
    }

    @PostMapping("/{sessionId}/checkpoint")
    public ResponseEntity<Void> checkpoint(@PathVariable String sessionId) {
        return playback.checkpoint(currentProfile.require().profileId(), sessionId)
                ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.GONE).build();
    }

    @GetMapping("/{sessionId}")
    public String showSession(@PathVariable String sessionId, Model model) {
        PlaybackSession session = playback.find(currentProfile.require().profileId(), sessionId);
        int position = playback.currentPosition(session);
        model.addAttribute("playbackSession", session);
        model.addAttribute("position", position);
        model.addAttribute("positionText", PlaybackTime.format(position));
        model.addAttribute("durationText", PlaybackTime.format(session.durationSeconds()));
        model.addAttribute("percent", PlaybackTime.percent(position, session.durationSeconds()));
        model.addAttribute("playable", playback.describe(session).orElse(null));
        return "viewing/player";
    }

    @PostMapping("/{sessionId}")
    public String control(@PathVariable String sessionId,
                          @RequestParam PlaybackSession.Action action,
                          @RequestParam(required = false) String position, RedirectAttributes redirect) {
        try {
            // La posición llega como hh:mm:ss (también se aceptan solo segundos).
            playback.apply(currentProfile.require().profileId(), sessionId, action, PlaybackTime.parse(position));
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/viewing/player/{sessionId}";
    }
}
