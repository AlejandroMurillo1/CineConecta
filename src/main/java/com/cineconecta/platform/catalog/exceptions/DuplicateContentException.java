package com.cineconecta.platform.catalog.exceptions;

import lombok.Getter;

import java.io.Serial;

@Getter
public class DuplicateContentException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String title;
    private final int year;

    public DuplicateContentException(String title, int year) {
        super("Ya existe un contenido titulado \"" + title + "\" del año " + year);
        this.title = title;
        this.year = year;
    }

}
