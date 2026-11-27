package com.cineconecta.platform.backoffice.internal.web;

import org.springframework.stereotype.Controller;
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

    @GetMapping({"", "/"})
    public String home() {
        return "backoffice/home";
    }
}
