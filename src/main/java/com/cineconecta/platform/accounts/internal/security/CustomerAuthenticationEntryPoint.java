package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.shared.security.AuthCookies;
import com.cineconecta.platform.shared.security.CookieBearerTokenResolver;
import com.cineconecta.platform.shared.security.TokenType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Qué hacer cuando una petición necesita un cliente autenticado y no lo hay (sin cookie, token
 * expirado, firma inválida o token de administrador).
 *
 * <ul>
 *   <li>Si había un token, se borra la cookie para no repetir el error en cada petición.</li>
 *   <li>Peticiones de htmx: 401 (app.js redirige al login). Una redirección dentro de un
 *       fragmento pintaría la página de login dentro del contenedor.</li>
 *   <li>Navegación normal: redirección a {@code /login}, recordando la página pedida si era GET.</li>
 * </ul>
 */
class CustomerAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthCookies cookies;
    private final CookieBearerTokenResolver tokenResolver = new CookieBearerTokenResolver(TokenType.CUSTOMER);

    CustomerAuthenticationEntryPoint(AuthCookies cookies) {
        this.cookies = cookies;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        boolean hadToken = tokenResolver.resolve(request) != null;
        if (hadToken) {
            cookies.clear(response, TokenType.CUSTOMER);
        }

        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UriComponentsBuilder login = UriComponentsBuilder.fromPath(request.getContextPath() + "/login");
        if (hadToken) {
            login.queryParam("expired");
        }
        if ("GET".equals(request.getMethod())) {
            String target = request.getRequestURI().substring(request.getContextPath().length());
            if (request.getQueryString() != null) {
                target += "?" + request.getQueryString();
            }
            login.queryParam("next", target);
        }
        response.sendRedirect(login.encode().build().toUriString());
    }
}
