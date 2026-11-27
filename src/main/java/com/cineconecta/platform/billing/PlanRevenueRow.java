package com.cineconecta.platform.billing;

import java.math.BigDecimal;

/**
 * Fila agregada de ingresos por plan en un rango de fechas [from, to).
 */
public record PlanRevenueRow(
        int planId,
        String planName,
        long paymentCount,
        BigDecimal totalRevenue
) {
}
