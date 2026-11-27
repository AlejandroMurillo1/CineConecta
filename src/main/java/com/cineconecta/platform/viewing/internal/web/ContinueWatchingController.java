package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/viewing/profiles/{profileId}/continue-watching")
public class ContinueWatchingController {
    private final PlaybackService playback;

    public ContinueWatchingController(PlaybackService playback) {
        this.playback = playback;
    }

    @GetMapping
    public String show(@PathVariable int profileId, Model model) {
        model.addAttribute("profileId", profileId);
        model.addAttribute("items", playback.continueWatching(profileId));
        return "viewing/continue-watching";
    }

    @PostMapping("/{sessionId}")
    public String resume(@PathVariable int profileId, @PathVariable String sessionId,
                         RedirectAttributes redirect) {
        try {
            redirect.addAttribute("nextSession", playback.resume(profileId, sessionId).id());
            return "redirect:/viewing/profiles/{profileId}/player/{nextSession}";
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
            return "redirect:/viewing/profiles/{profileId}/continue-watching";
        }
    }
}
