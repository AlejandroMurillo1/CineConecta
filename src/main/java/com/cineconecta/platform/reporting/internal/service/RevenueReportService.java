package com.cineconecta.platform.reporting.internal.service;

import com.cineconecta.platform.billing.PlanRevenueRow;
import com.cineconecta.platform.billing.RevenueApi;
import com.cineconecta.platform.reporting.internal.csv.CsvWriter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma el informe de ingresos por plan sobre {@link RevenueApi} (módulo billing).
 * No accede a la base de datos directamente: la consulta pertenece a billing.
 */
@Service
public class RevenueReportService {

    static final List<String> CSV_HEADER = List.of(
            "Plan", "Suscripciones adquiridas", "Total recaudado (COP)");

    private final RevenueApi revenueApi;

    public RevenueReportService(RevenueApi revenueApi) {
        this.revenueApi = revenueApi;
    }

    public RevenueReport revenueByPlan(ReportPeriod period) {
        List<PlanRevenueRow> rows = List.copyOf(
                revenueApi.revenueByPlan(period.startInclusive(), period.endExclusive()));
        long totalSubscriptions = rows.stream().mapToLong(PlanRevenueRow::paymentCount).sum();
        BigDecimal totalRevenue = rows.stream()
                .map(PlanRevenueRow::totalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RevenueReport(period, rows, totalSubscriptions, totalRevenue);
    }

    /** El mismo informe en CSV (UTF-8 con BOM), con una fila final de totales. */
    public byte[] revenueByPlanCsv(ReportPeriod period) {
        RevenueReport report = revenueByPlan(period);
        List<List<?>> lines = new ArrayList<>();
        for (PlanRevenueRow row : report.rows()) {
            lines.add(List.of(row.planName(), row.paymentCount(), row.totalRevenue()));
        }
        lines.add(List.of("TOTAL", report.totalSubscriptions(), report.totalRevenue()));
        return CsvWriter.toUtf8WithBom(CSV_HEADER, lines);
    }

    /** Nombre del archivo descargado, por ejemplo {@code ingresos-por-plan_2026-11-01_2026-11-30.csv}. */
    public static String csvFileName(ReportPeriod period) {
        return "ingresos-por-plan_" + period.from() + "_" + period.to() + ".csv";
    }
}
