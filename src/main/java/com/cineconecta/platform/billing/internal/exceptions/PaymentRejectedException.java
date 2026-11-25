package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;

public class PaymentRejectedException extends BillingException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentRejectedException(String reason) {
        super("Pago rechazado: " + reason + " No se registró ninguna suscripción ni cobro.");
    }
}
