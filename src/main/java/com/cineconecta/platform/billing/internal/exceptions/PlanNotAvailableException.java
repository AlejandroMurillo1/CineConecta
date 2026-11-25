package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;

public class PlanNotAvailableException extends BillingException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PlanNotAvailableException(int planId) {
        super("El plan seleccionado (" + planId + ") no existe o no está activo para nuevas contrataciones.");
    }
}
