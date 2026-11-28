package com.cineconecta.platform.billing.internal.service;

import com.cineconecta.platform.billing.PlanRevenueRow;
import com.cineconecta.platform.billing.RevenueApi;
import com.cineconecta.platform.shared.persistence.ReportingJdbc;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ingresos por plan para el informe de la administración, leídos con el usuario {@code cine_report}
 * (solo lectura) en lugar de {@code cine_app}.
 *
 * <p>Es {@code @Primary} porque {@link BillingQueryService} también implementa {@link RevenueApi};
 * así el módulo reporting recibe esta implementación. Cuando se quite {@code RevenueApi} de
 * {@code BillingQueryService}, se puede quitar {@code @Primary}.</p>
 */
@Service
@Primary
public class RevenueReportQuery implements RevenueApi {

    private final ReportingJdbc reportingJdbc;

    public RevenueReportQuery(ReportingJdbc reportingJdbc) {
        this.reportingJdbc = reportingJdbc;
    }

    @Override
    public List<PlanRevenueRow> revenueByPlan(Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "La fecha inicial es obligatoria");
        Objects.requireNonNull(toExclusive, "La fecha final es obligatoria");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("El rango de fechas debe cumplir fromInclusive < toExclusive");
        }

        return reportingJdbc.template().query(
                """
                SELECT pl.planid,
                       pl.name,
                       COUNT(pay.paymentid) AS payment_count,
                       COALESCE(SUM(pay.amount), 0) AS total_revenue
                FROM cineconecta.plans pl
                LEFT JOIN cineconecta.subscriptions sub ON sub.planid = pl.planid
                LEFT JOIN cineconecta.payments pay
                       ON pay.subscriptionid = sub.subscriptionid
                      AND pay.paidat >= ?
                      AND pay.paidat < ?
                GROUP BY pl.planid, pl.name
                ORDER BY pl.planid
                """,
                (rs, rowNum) -> new PlanRevenueRow(
                        rs.getInt("planid"),
                        rs.getString("name"),
                        rs.getLong("payment_count"),
                        rs.getBigDecimal("total_revenue") != null
                                ? rs.getBigDecimal("total_revenue")
                                : BigDecimal.ZERO
                ),
                Timestamp.from(fromInclusive),
                Timestamp.from(toExclusive)
        );
    }
}
