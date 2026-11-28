package com.cineconecta.platform.accounts.internal.web;

import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.security.CurrentProfileResolver;
import com.cineconecta.platform.accounts.internal.security.CustomerTokens;
import com.cineconecta.platform.accounts.internal.service.ProfileRuleException;
import com.cineconecta.platform.accounts.internal.service.ProfileService;
import com.cineconecta.platform.shared.CurrentAccountProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Perfiles de la cuenta autenticada (RF-05 a RF-09). El {@code accountId} sale siempre del token;
 * los ids de perfil de la URL se buscan junto con la cuenta, así que un perfil ajeno da 404.
 */
@Controller
@RequestMapping("/profiles")
public class ProfileController {

    static final String AFTER_SELECT = "/catalog";

    /** Clasificaciones que ofrece el formulario (el servidor acepta cualquier valor de 0 a 18). */
    static final List<Integer> RATING_OPTIONS = List.of(0, 7, 12, 16, 18);

    private static final Map<String, String> SUCCESS_MESSAGES = Map.of(
            "created", "Perfil creado.",
            "updated", "Perfil actualizado.",
            "deactivated", "Perfil desactivado. Su historial se conserva.",
            "reactivated", "Perfil reactivado.");

    private final ProfileService profiles;
    private final CurrentAccountProvider currentAccount;
    private final CurrentProfileResolver currentProfile;
    private final CustomerTokens tokens;

    public ProfileController(ProfileService profiles, CurrentAccountProvider currentAccount,
                             CurrentProfileResolver currentProfile, CustomerTokens tokens) {
        this.profiles = profiles;
        this.currentAccount = currentAccount;
        this.currentProfile = currentProfile;
        this.tokens = tokens;
    }

    // ---------- RF-08 listar / RF-09 elegir ----------

    @GetMapping
    public String list(@RequestParam(required = false) String next,
                       @RequestParam(required = false) String ok,
                       Model model) {
        if (ok != null && SUCCESS_MESSAGES.containsKey(ok)) {
            model.addAttribute("success", SUCCESS_MESSAGES.get(ok));
        }
        return page(model, new ProfileForm(), next);
    }

    @PostMapping("/{profileId}/select")
    public String select(@PathVariable int profileId, @RequestParam(required = false) String next,
                         Model model, HttpServletResponse response) {
        int accountId = currentAccount.requireCurrentAccountId();
        return profiles.selectable(accountId, profileId)
                .map(profile -> {
                    tokens.signIn(response, accountId, profile.profileId());
                    return "redirect:" + AuthController.safeNext(next, AFTER_SELECT);
                })
                .orElseGet(() -> {
                    response.setStatus(HttpStatus.CONFLICT.value());
                    model.addAttribute("error", "Ese perfil no está disponible. Elige un perfil activo.");
                    return page(model, new ProfileForm(), next);
                });
    }

    // ---------- RF-05 crear ----------

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProfileForm form, BindingResult errors,
                         Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return page(model, form, null);
        }
        try {
            profiles.create(currentAccount.requireCurrentAccountId(), form.getName(), form.getMaxAgeRating());
            return "redirect:/profiles?ok=created";
        } catch (ProfileRuleException rule) {
            response.setStatus(statusFor(rule).value());
            model.addAttribute("error", rule.getMessage());
            return page(model, form, null);
        }
    }

    // ---------- RF-06 editar ----------

    @GetMapping("/{profileId}/edit")
    public String editPage(@PathVariable int profileId, Model model) {
        ProfileView profile = owned(profileId);
        return editView(model, profile, ProfileForm.of(profile));
    }

    @PostMapping("/{profileId}")
    public String update(@PathVariable int profileId, @Valid @ModelAttribute("form") ProfileForm form,
                         BindingResult errors, Model model, HttpServletResponse response) {
        ProfileView profile = owned(profileId);
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return editView(model, profile, form);
        }
        try {
            profiles.update(profile.accountId(), profileId, form.getName(), form.getMaxAgeRating());
            return "redirect:/profiles?ok=updated";
        } catch (ProfileRuleException rule) {
            response.setStatus(statusFor(rule).value());
            model.addAttribute("error", rule.getMessage());
            return editView(model, profile, form);
        }
    }

    // ---------- RF-07 desactivar / reactivar ----------

    @PostMapping("/{profileId}/deactivate")
    public String deactivate(@PathVariable int profileId, HttpServletResponse response) {
        ProfileView profile = owned(profileId);
        profiles.deactivate(profile.accountId(), profileId);
        // Si era el perfil en uso, se reemite el token sin profileId (igual dejaría de aceptarse,
        // porque cada petición vuelve a verificar que el perfil siga activo).
        currentProfile.profileIdClaim().ifPresent(inUse -> {
            if (inUse == profileId) {
                tokens.signIn(response, profile.accountId(), null);
            }
        });
        return "redirect:/profiles?ok=deactivated";
    }

    @PostMapping("/{profileId}/reactivate")
    public String reactivate(@PathVariable int profileId, Model model, HttpServletResponse response) {
        ProfileView profile = owned(profileId);
        try {
            profiles.reactivate(profile.accountId(), profileId);
            return "redirect:/profiles?ok=reactivated";
        } catch (ProfileRuleException rule) {
            response.setStatus(statusFor(rule).value());
            model.addAttribute("error", rule.getMessage());
            return page(model, new ProfileForm(), null);
        }
    }

    // ---------- apoyo ----------

    private ProfileView owned(int profileId) {
        return profiles.find(currentAccount.requireCurrentAccountId(), profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String page(Model model, ProfileForm form, String next) {
        model.addAttribute("overview", profiles.overview(currentAccount.requireCurrentAccountId()));
        model.addAttribute("form", form);
        model.addAttribute("ratingOptions", RATING_OPTIONS);
        model.addAttribute("next", AuthController.safeNext(next, ""));
        return "accounts/profiles";
    }

    private String editView(Model model, ProfileView profile, ProfileForm form) {
        TreeSet<Integer> options = new TreeSet<>(RATING_OPTIONS);
        options.add(profile.maxAgeRating());
        model.addAttribute("profile", profile);
        model.addAttribute("form", form);
        model.addAttribute("ratingOptions", options);
        return "accounts/profile-edit";
    }

    private static HttpStatus statusFor(ProfileRuleException rule) {
        return switch (rule.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NO_ACTIVE_SUBSCRIPTION, PROFILE_LIMIT_REACHED, DUPLICATE_NAME -> HttpStatus.CONFLICT;
        };
    }
}
