package com.cineconecta.platform.billing;

import java.time.Instant;
import java.util.Optional;

/**
 * API pública del módulo billing para consultar la suscripción vigente o histórica de una cuenta.
 */
public interface SubscriptionApi {

    Optional<SubscriptionView> current(int accountId, Instant at);

    Optional<PlanSummaryView> planAt(int accountId, Instant at);
}
