package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;

public class DuplicatePlanNameException extends BillingException {
    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicatePlanNameException(String name) {
        super("Ya existe un plan con el nombre \"" + name + "\".");
    }
}
