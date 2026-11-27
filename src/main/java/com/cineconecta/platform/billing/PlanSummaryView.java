package com.cineconecta.platform.billing;

import java.math.BigDecimal;

/**
 * Vista pública resumida de un plan de suscripción.
 */
public record PlanSummaryView(
        int planId,
        String name,
        BigDecimal price,
        int maxProfiles,
        boolean active
) {
}
