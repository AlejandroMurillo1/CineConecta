package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.viewing.internal.service.MyListService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/viewing/profiles/{profileId}/my-list")
public class MyListController {
    private final MyListService service;

    public MyListController(MyListService service) {
        this.service = service;
    }

    @GetMapping
    public String show(@PathVariable int profileId, Model model) {
        model.addAttribute("profileId", profileId);
        model.addAttribute("titles", service.list(profileId));
        return "viewing/my-list";
    }

    @PostMapping
    public String add(@PathVariable int profileId, @RequestParam String contentId, RedirectAttributes redirect) {
        try {
            service.add(profileId, contentId);
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/viewing/profiles/{profileId}/my-list";
    }

    @PostMapping("/remove")
    public String remove(@PathVariable int profileId, @RequestParam String contentId, RedirectAttributes redirect) {
        try {
            service.remove(profileId, contentId);
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/viewing/profiles/{profileId}/my-list";
    }
}
