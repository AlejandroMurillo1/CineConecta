package com.cineconecta.platform.reporting.internal.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Informe de consumo por plan y tipo de contenido: minutos reproducidos y perfiles distintos,
 * a partir de las sesiones de reproducción registradas en MongoDB.
 *
 * <p>El plan de cada sesión es el que tenía la cuenta al iniciarla ({@code planIdAtStart}).
 * Los perfiles distintos no se suman en el total: un mismo perfil puede aparecer en varias filas.</p>
 */
public record ConsumptionReport(
        ReportPeriod period,
        List<ConsumptionReportRow> rows,
        BigDecimal totalMinutes
) {

    public boolean hasData() {
        return !rows.isEmpty();
    }
}
