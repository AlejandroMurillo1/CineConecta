package com.cineconecta.platform.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Página de inicio pública. Solo enlaza a las pantallas de los demás módulos; no consulta datos.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "shared/home";
    }
}
