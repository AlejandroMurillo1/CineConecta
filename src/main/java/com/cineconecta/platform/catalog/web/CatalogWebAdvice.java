package com.cineconecta.platform.catalog.web;

import com.cineconecta.platform.catalog.exceptions.ContentNotFoundException;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.exceptions.StaleContentException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Errores que los controladores del catálogo no resuelven por sí mismos. Los formularios y las
 * acciones (publicar, retirar...) atrapan sus propios errores para conservar lo escrito o mostrar un
 * mensaje en la misma página; esto cubre el resto (p. ej. abrir un contenido que no existe).
 */
@ControllerAdvice(basePackageClasses = CatalogWebAdvice.class)
public class CatalogWebAdvice {

    @ExceptionHandler(ContentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(ContentNotFoundException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/not-found";
    }

    @ExceptionHandler({ContentRuleException.class, StaleContentException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(RuntimeException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/conflict";
    }
}
