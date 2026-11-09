package com.cineconecta.platform.catalog.exceptions;

import java.io.Serial;

public class ContentNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ContentNotFoundException(String message) {
        super(message);
    }
}
