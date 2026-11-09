package com.cineconecta.platform.catalog.exceptions;

import java.io.Serial;

public class ContentRuleException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ContentRuleException(String message) {
        super(message);
    }
}
