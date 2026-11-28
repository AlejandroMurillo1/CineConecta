package com.cineconecta.platform.billing.internal.web;

import com.cineconecta.platform.billing.AdminPlanView;
import com.cineconecta.platform.billing.PlanApi;
import com.cineconecta.platform.billing.internal.exceptions.BillingException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gestión de planes por el administrador (RF-12.1 a RF-12.4).
 *
 * <p>Vive en billing porque usa sus reglas y excepciones internas; se publica bajo
 * {@code /admin/**} para quedar detrás de la cadena de seguridad de administradores del módulo
 * backoffice (rol ADMIN). El precio y el máximo de perfiles no se editan: {@code cine_app} solo
 * tiene {@code UPDATE (name, isActive)} sobre {@code plans}, y cambiarlos alteraría lo que ya
 * pagaron las suscripciones vigentes.</p>
 */
@Controller
@RequestMapping("/admin/plans")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlansController {

    static final String VIEW = "billing/admin-plans";

    private final PlanApi plans;

    public AdminPlansController(PlanApi plans) {
        this.plans = plans;
    }

    /** RF-12.1: todos los planes, con su estado y sus suscripciones vigentes. */
    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AdminPlanForm());
        }
        model.addAttribute("plans", plans.listPlansForAdmin());
        return VIEW;
    }

    /** RF-12.2: crea un plan activo. Los errores se muestran junto al formulario. */
    @PostMapping
    public String create(@Valid @ModelAttribute("form") AdminPlanForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                AdminPlanView created = plans.createPlan(form.getName(), form.getPrice(), form.getMaxProfiles());
                redirect.addFlashAttribute("success", "Plan \"" + created.name() + "\" creado y disponible para contratar.");
                return "redirect:/admin/plans";
            } catch (BillingException | IllegalArgumentException ex) {
                result.reject("plan", ex.getMessage());
            }
        }
        model.addAttribute("plans", plans.listPlansForAdmin());
        return VIEW;
    }

    /** RF-12.4: cambia el nombre de un plan. */
    @PostMapping("/{planId}/rename")
    public String rename(@PathVariable int planId, @RequestParam(defaultValue = "") String name,
                         RedirectAttributes redirect) {
        return apply(redirect, "Nombre del plan actualizado a \"" + name.strip() + "\".",
                () -> plans.renamePlan(planId, name));
    }

    /** RF-12.3: desactiva un plan. Deja de ofrecerse, pero las suscripciones vigentes siguen hasta vencer. */
    @PostMapping("/{planId}/deactivate")
    public String deactivate(@PathVariable int planId, RedirectAttributes redirect) {
        return apply(redirect, "Plan desactivado: ya no se ofrece a los clientes.",
                () -> plans.setPlanActive(planId, false));
    }

    /** RF-12.3: reactiva un plan. */
    @PostMapping("/{planId}/activate")
    public String activate(@PathVariable int planId, RedirectAttributes redirect) {
        return apply(redirect, "Plan reactivado: vuelve a ofrecerse a los clientes.",
                () -> plans.setPlanActive(planId, true));
    }

    private static String apply(RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("success", success);
        } catch (BillingException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/plans";
    }
}
