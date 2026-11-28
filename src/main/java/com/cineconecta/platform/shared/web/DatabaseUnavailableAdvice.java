package com.cineconecta.platform.shared.web;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;

/**
 * RNF-07: si una base de datos no está disponible, la aplicación responde <b>503 Servicio no
 * disponible</b> con una página que dice qué parte del servicio está afectada, en lugar de un 500
 * genérico o una espera indefinida.
 *
 * <ul>
 *   <li>PostgreSQL caído: no se puede abrir la conexión ni la transacción
 *       ({@code CannotCreateTransactionException}, {@code CannotGetJdbcConnectionException}).</li>
 *   <li>MongoDB caído: el driver no encuentra el servidor ({@code MongoTimeoutException},
 *       {@code MongoSocketException}), traducidas o no por Spring Data.</li>
 * </ul>
 *
 * <p>Una operación interrumpida en PostgreSQL se revierte completa, porque su transacción nunca
 * se confirma. Una escritura en MongoDB no forma parte de esa transacción (ver la matriz de fallos
 * en docs). La espera máxima para detectar la caída es de 5 s por base (ver
 * {@code spring.datasource.hikari.connection-timeout} y {@code DatabaseTimeoutsConfig}).</p>
 *
 * <p>Nunca muestra el mensaje ni la traza de la excepción al usuario; los registra en el log.</p>
 */
@ControllerAdvice
public class DatabaseUnavailableAdvice {

    /** Qué base falló; la plantilla de error lo usa para explicar qué parte del servicio está afectada. */
    public enum Database { POSTGRES, MONGO, UNKNOWN }

    static final String RETRY_AFTER_SECONDS = "30";

    private static final Logger log = LoggerFactory.getLogger(DatabaseUnavailableAdvice.class);

    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class,
            TransientDataAccessResourceException.class,
            MongoTimeoutException.class,
            MongoSocketException.class
    })
    public ModelAndView databaseUnavailable(Exception exception, HttpServletRequest request,
                                            HttpServletResponse response) {
        Database database = identify(exception);
        log.warn("Base de datos no disponible ({}) en {} {}: {}", database, request.getMethod(),
                request.getRequestURI(), rootCause(exception).toString());

        response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
        ModelAndView view = new ModelAndView("error");
        view.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
        view.addObject("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        view.addObject("unavailableDatabase", database.name());
        return view;
    }

    static Database identify(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException) {
                return Database.MONGO;
            }
            if (cause instanceof SQLException || cause instanceof CannotCreateTransactionException) {
                return Database.POSTGRES;
            }
        }
        return Database.UNKNOWN;
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
