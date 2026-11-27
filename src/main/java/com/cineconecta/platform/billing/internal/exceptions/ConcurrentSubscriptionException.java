package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;

public class ConcurrentSubscriptionException extends BillingException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrentSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
