package com.cineconecta.platform.backoffice.internal.security;

import com.mongodb.MongoException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * Distingue "credenciales incorrectas" de "no se pudo verificar" en el inicio de sesión de administradores.
 *
 * <p>Los administradores están en MongoDB. Si MongoDB no responde, Spring Security envuelve el error en
 * {@link InternalAuthenticationServiceException} y, sin este manejador, lo mostraría como credenciales
 * incorrectas. Aquí se responde 503 (RNF-07) con la página de inicio de sesión y un aviso, sin revelar
 * detalles internos. El {@code @ControllerAdvice} de 503 de shared no llega a actuar, porque la
 * autenticación ocurre en el filtro de seguridad, antes de cualquier controlador.</p>
 */
public class AdminLoginFailureHandler implements AuthenticationFailureHandler {

    /** Ruta interna a la que se reenvía la petición para pintar el aviso (ver AdminController). */
    public static final String UNAVAILABLE_PATH = "/admin/login/unavailable";

    static final String RETRY_AFTER_SECONDS = "30";

    private static final Logger log = LoggerFactory.getLogger(AdminLoginFailureHandler.class);

    private final AuthenticationFailureHandler badCredentials =
            new SimpleUrlAuthenticationFailureHandler("/admin/login?error");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof InternalAuthenticationServiceException && databaseUnavailable(exception)) {
            log.warn("Inicio de sesión de administrador sin verificar: MongoDB no disponible ({})",
                    rootCause(exception).toString());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
            request.getRequestDispatcher(UNAVAILABLE_PATH).forward(request, response);
            return;
        }
        badCredentials.onAuthenticationFailure(request, response, exception);
    }

    /** MongoDB caído: errores del driver (sin traducir) o traducidos por Spring Data. */
    static boolean databaseUnavailable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException || cause instanceof DataAccessResourceFailureException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
