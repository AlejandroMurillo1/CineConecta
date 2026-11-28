package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;

public class PlanNotFoundException extends BillingException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PlanNotFoundException(int planId) {
        super("El plan " + planId + " no existe.");
    }
}
