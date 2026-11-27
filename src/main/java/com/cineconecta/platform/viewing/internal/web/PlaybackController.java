package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession;
import com.cineconecta.platform.viewing.internal.service.ViewingAccess;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/viewing/profiles/{profileId}/player")
public class PlaybackController {
    private final ViewingAccess access;
    private final PlaybackService playback;

    public PlaybackController(ViewingAccess access, PlaybackService playback) {
        this.access = access;
        this.playback = playback;
    }

    @GetMapping
    public String showStart(@PathVariable int profileId, Model model) {
        access.requireEligibleProfile(profileId);
        model.addAttribute("profileId", profileId);
        return "viewing/player";
    }

    @PostMapping
    public String start(@PathVariable int profileId, @RequestParam String contentId,
                        @RequestParam(required = false) String episodeId, RedirectAttributes redirect) {
        try {
            redirect.addAttribute("sessionId", playback.start(profileId, contentId, episodeId).id());
            return "redirect:/viewing/profiles/{profileId}/player/{sessionId}";
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
            return "redirect:/viewing/profiles/{profileId}/player";
        }
    }

    @PostMapping("/{sessionId}/checkpoint")
    public ResponseEntity<Void> checkpoint(@PathVariable int profileId, @PathVariable String sessionId) {
        return playback.checkpoint(profileId, sessionId)
                ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.GONE).build();
    }

    @GetMapping("/{sessionId}")
    public String showSession(@PathVariable int profileId, @PathVariable String sessionId, Model model) {
        PlaybackSession session = playback.find(profileId, sessionId);
        model.addAttribute("profileId", profileId);
        model.addAttribute("playbackSession", session);
        model.addAttribute("position", playback.currentPosition(session));
        return "viewing/player";
    }

    @PostMapping("/{sessionId}")
    public String control(@PathVariable int profileId, @PathVariable String sessionId,
                          @RequestParam PlaybackSession.Action action,
                          @RequestParam(required = false) Integer position, RedirectAttributes redirect) {
        try {
            playback.apply(profileId, sessionId, action, position);
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/viewing/profiles/{profileId}/player/{sessionId}";
    }
}
