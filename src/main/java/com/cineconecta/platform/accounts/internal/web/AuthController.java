package com.cineconecta.platform.accounts.internal.web;

import com.cineconecta.platform.accounts.internal.service.AccountService;
import com.cineconecta.platform.accounts.internal.service.AccountSummary;
import com.cineconecta.platform.accounts.internal.service.EmailAlreadyRegisteredException;
import com.cineconecta.platform.shared.security.AuthCookies;
import com.cineconecta.platform.shared.security.CineClaims;
import com.cineconecta.platform.shared.security.IssuedToken;
import com.cineconecta.platform.shared.security.JwtTokenService;
import com.cineconecta.platform.shared.security.TokenType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Registro, inicio de sesión de clientes (RF-01, RF-02). El cierre de sesión (RF-03) lo atiende
 * el {@code LogoutFilter} de la cadena de clientes en {@code POST /logout}.
 */
@Controller
public class AuthController {

    static final String DEFAULT_AFTER_LOGIN = "/";
    static final String AFTER_REGISTER = "/billing/plans";

    private final AccountService accounts;
    private final JwtTokenService tokens;
    private final AuthCookies cookies;

    public AuthController(AccountService accounts, JwtTokenService tokens, AuthCookies cookies) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.cookies = cookies;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next,
                            @RequestParam(required = false) String expired,
                            @RequestParam(required = false) String logout,
                            Model model) {
        model.addAttribute("next", safeNext(next, ""));
        if (expired != null) {
            model.addAttribute("info", "Tu sesión expiró. Inicia sesión de nuevo.");
        } else if (logout != null) {
            model.addAttribute("success", "Cerraste sesión.");
        }
        return "accounts/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(defaultValue = "") String email,
                        @RequestParam(defaultValue = "") String password,
                        @RequestParam(required = false) String next,
                        Model model, HttpServletResponse response) {
        return accounts.authenticate(email, password)
                .map(account -> {
                    signIn(account, response);
                    return "redirect:" + safeNext(next, DEFAULT_AFTER_LOGIN);
                })
                .orElseGet(() -> {
                    model.addAttribute("email", email);
                    model.addAttribute("next", safeNext(next, ""));
                    model.addAttribute("error", "Correo o contraseña incorrectos.");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return "accounts/login";
                });
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "accounts/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult errors,
                           HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "accounts/register";
        }
        try {
            AccountSummary account = accounts.register(form.getFullName(), form.getEmail(), form.getPassword());
            signIn(account, response);
            return "redirect:" + AFTER_REGISTER;
        } catch (EmailAlreadyRegisteredException duplicate) {
            errors.rejectValue("email", "duplicate", "Ya existe una cuenta con este correo.");
        } catch (IllegalArgumentException invalid) {
            errors.rejectValue("password", "invalid", invalid.getMessage());
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "accounts/register";
    }

    private void signIn(AccountSummary account, HttpServletResponse response) {
        IssuedToken token = tokens.issue(TokenType.CUSTOMER, String.valueOf(account.accountId()),
                Map.of(CineClaims.ACCOUNT_ID, account.accountId()));
        cookies.write(response, token);
    }

    /**
     * Solo se aceptan rutas locales ("/algo"). Evita redirecciones abiertas como
     * {@code //sitio-malicioso.com} o {@code https://...} y no deja volver a /login o /register.
     */
    static String safeNext(String next, String fallback) {
        if (next == null || next.isBlank() || !next.startsWith("/")
                || next.startsWith("//") || next.startsWith("/\\") || next.contains("\r") || next.contains("\n")
                || next.startsWith("/login") || next.startsWith("/register")) {
            return fallback;
        }
        return next;
    }
}
