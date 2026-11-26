package com.cineconecta.platform.reporting.internal.service;

import com.cineconecta.platform.billing.PlanRevenueRow;

import java.math.BigDecimal;
import java.util.List;

/**
 * Informe de ingresos por plan (RF-33): total recaudado y suscripciones adquiridas en el período,
 * a partir de los pagos registrados en PostgreSQL. Cada pago respalda exactamente una suscripción,
 * así que el número de pagos es el número de suscripciones adquiridas.
 */
public record RevenueReport(
        ReportPeriod period,
        List<PlanRevenueRow> rows,
        long totalSubscriptions,
        BigDecimal totalRevenue
) {

    public boolean hasData() {
        return totalSubscriptions > 0;
    }
}
