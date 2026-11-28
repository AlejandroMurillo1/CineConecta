package com.cineconecta.platform.reporting.internal.service;

import com.cineconecta.platform.billing.PlanApi;
import com.cineconecta.platform.reporting.internal.csv.CsvWriter;
import com.cineconecta.platform.viewing.ConsumptionApi;
import com.cineconecta.platform.viewing.ConsumptionRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arma el informe de consumo combinando las dos bases a través de las APIs de los módulos:
 * los minutos y perfiles salen de {@link ConsumptionApi} (viewing, MongoDB) y los nombres de
 * los planes de {@link PlanApi} (billing, PostgreSQL). No escribe en ninguna de las dos.
 */
@Service
public class ConsumptionReportService {

    static final List<String> CSV_HEADER = List.of(
            "Plan", "Tipo de contenido", "Minutos consumidos", "Perfiles distintos");

    private final ConsumptionApi consumptionApi;
    private final PlanApi planApi;

    public ConsumptionReportService(ConsumptionApi consumptionApi, PlanApi planApi) {
        this.consumptionApi = consumptionApi;
        this.planApi = planApi;
    }

    public ConsumptionReport consumptionByPlanAndType(ReportPeriod period) {
        List<ConsumptionRow> raw = consumptionApi.byPlanAndType(period.startInclusive(), period.endExclusive());
        Map<Integer, String> planNames = planApi.namesByIds(raw.stream().map(ConsumptionRow::planId).toList());
        List<ConsumptionReportRow> rows = raw.stream()
                .map(row -> new ConsumptionReportRow(
                        row.planId(),
                        // Un plan que ya no esté en PostgreSQL no debería existir, pero el informe no se cae por eso.
                        planNames.getOrDefault(row.planId(), "Plan " + row.planId()),
                        row.type(),
                        row.minutes(),
                        row.distinctProfiles()))
                .toList();
        BigDecimal totalMinutes = rows.stream()
                .map(ConsumptionReportRow::minutes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ConsumptionReport(period, rows, totalMinutes);
    }

    /** El mismo informe en CSV (UTF-8 con BOM), con una fila final con el total de minutos. */
    public byte[] consumptionByPlanAndTypeCsv(ReportPeriod period) {
        ConsumptionReport report = consumptionByPlanAndType(period);
        List<List<?>> lines = new ArrayList<>();
        for (ConsumptionReportRow row : report.rows()) {
            lines.add(List.of(row.planName(), row.typeLabel(), row.minutes(), row.distinctProfiles()));
        }
        lines.add(List.of("TOTAL", "", report.totalMinutes(), ""));
        return CsvWriter.toUtf8WithBom(CSV_HEADER, lines);
    }

    /** Nombre del archivo descargado, por ejemplo {@code consumo-por-plan_2026-11-01_2026-11-30.csv}. */
    public static String csvFileName(ReportPeriod period) {
        return "consumo-por-plan_" + period.from() + "_" + period.to() + ".csv";
    }
}
