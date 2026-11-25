package com.cineconecta.platform.billing;

import java.time.Instant;
import java.util.List;

/**
 * API pública del módulo billing para el informe de ingresos por plan.
 */
public interface RevenueApi {

    List<PlanRevenueRow> revenueByPlan(Instant fromInclusive, Instant toExclusive);
}
