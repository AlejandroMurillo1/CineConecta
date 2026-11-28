package com.cineconecta.platform.billing;

import java.math.BigDecimal;

/**
 * Vista de un plan para el administrador, con sus suscripciones vigentes.
 */
public record AdminPlanView(
        int planId,
        String name,
        BigDecimal price,
        int maxProfiles,
        boolean active,
        long activeSubscriptions
) {
}
