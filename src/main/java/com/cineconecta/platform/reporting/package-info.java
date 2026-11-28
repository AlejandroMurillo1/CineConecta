/**
 * Módulo de informes para la administración.
 *
 * <p>No expone API pública: solo consume las APIs de otros módulos
 * ({@code billing.RevenueApi} y {@code billing.PlanApi} sobre PostgreSQL,
 * {@code viewing.ConsumptionApi} sobre MongoDB) y publica las pantallas y exportaciones CSV
 * bajo {@code /admin/reports/**}: ingresos por plan y consumo por plan y tipo de contenido.</p>
 */
package com.cineconecta.platform.reporting;
