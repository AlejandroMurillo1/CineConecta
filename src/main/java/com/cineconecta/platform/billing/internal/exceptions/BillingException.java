package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;

public abstract class BillingException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    protected BillingException(String message) {
        super(message);
    }

    protected BillingException(String message, Throwable cause) {
        super(message, cause);
    }
}
