package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;
import java.time.Instant;

public class ActiveSubscriptionAlreadyExistsException extends BillingException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ActiveSubscriptionAlreadyExistsException(Instant endsAt) {
        super("La cuenta ya tiene una suscripción vigente hasta " + endsAt + ". No se permiten períodos superpuestos.");
    }
}
