package com.cineconecta.platform.billing.internal.service;

import com.cineconecta.platform.billing.internal.exceptions.PaymentRejectedException;
import com.cineconecta.platform.billing.internal.persistence.PlanEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Pasarela simulada para la contratación de planes.
 */
@Component
public class SimulatedPaymentGateway {

    public String processPayment(PlanEntity plan, PaymentSimulationMode mode) {
        if (mode == null || mode == PaymentSimulationMode.REJECTED) {
            throw new PaymentRejectedException("la entidad financiera simulada rechazó la transacción.");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        return "SIM-" + plan.getPlanId() + "-" + suffix;
    }
}
