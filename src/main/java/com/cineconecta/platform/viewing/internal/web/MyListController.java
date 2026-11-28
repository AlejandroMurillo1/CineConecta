package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.accounts.CurrentProfile;
import com.cineconecta.platform.viewing.internal.service.MyListService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Mi lista" del perfil en uso. El perfil sale de la sesión ({@link CurrentProfile}), no de la URL;
 * el servicio vuelve a verificar cuenta, perfil activo y suscripción con ViewingAccess.
 */
@Controller
@RequestMapping("/viewing/my-list")
public class MyListController {
    private final MyListService service;
    private final CurrentProfile currentProfile;

    public MyListController(MyListService service, CurrentProfile currentProfile) {
        this.service = service;
        this.currentProfile = currentProfile;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("titles", service.list(currentProfile.require().profileId()));
        return "viewing/my-list";
    }

    @PostMapping
    public String add(@RequestParam String contentId, RedirectAttributes redirect) {
        try {
            service.add(currentProfile.require().profileId(), contentId);
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/viewing/my-list";
    }

    @PostMapping("/remove")
    public String remove(@RequestParam String contentId, RedirectAttributes redirect) {
        try {
            service.remove(currentProfile.require().profileId(), contentId);
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/viewing/my-list";
    }
}
