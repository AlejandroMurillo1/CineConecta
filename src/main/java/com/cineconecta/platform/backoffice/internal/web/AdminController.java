package com.cineconecta.platform.backoffice.internal.web;

import org.springframework.stereotype.Controller;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Inicio de sesión y panel principal de administración. El POST de {@code /admin/login} y
 * {@code /admin/logout} los atiende Spring Security (ver {@code AdminSecurityConfig}).
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/login")
    public String login() {
        return "backoffice/login";
    }

    /**
     * Aviso de que no se pudo verificar el inicio de sesión porque MongoDB no responde (RNF-07).
     * Llega por reenvío desde {@code AdminLoginFailureHandler}, por eso acepta cualquier método.
     */
    @RequestMapping("/login/unavailable")
    public String loginUnavailable(Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setHeader("Retry-After", "30");
        model.addAttribute("unavailable", true);
        return "backoffice/login";
    }

    @GetMapping({"", "/"})
    public String home() {
        return "backoffice/home";
    }
}
