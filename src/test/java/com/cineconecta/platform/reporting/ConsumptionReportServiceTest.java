package com.cineconecta.platform.reporting;

import com.cineconecta.platform.billing.PlanApi;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.reporting.internal.service.ConsumptionReport;
import com.cineconecta.platform.reporting.internal.service.ConsumptionReportService;
import com.cineconecta.platform.reporting.internal.service.ReportPeriod;
import com.cineconecta.platform.viewing.ConsumptionApi;
import com.cineconecta.platform.viewing.ConsumptionRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumptionReportServiceTest {

    private static final ReportPeriod NOVEMBER =
            new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    private final ConsumptionApi consumptionApi = mock(ConsumptionApi.class);
    private final PlanApi planApi = mock(PlanApi.class);
    private final ConsumptionReportService service = new ConsumptionReportService(consumptionApi, planApi);

    @Test
    void asksViewingForTheHalfOpenRangeAndNamesThePlans() {
        when(consumptionApi.byPlanAndType(NOVEMBER.startInclusive(), NOVEMBER.endExclusive())).thenReturn(List.of(
                new ConsumptionRow(1, ContentType.MOVIE, new BigDecimal("120.50"), 2),
                new ConsumptionRow(2, ContentType.SERIES, new BigDecimal("300.00"), 3)));
        when(planApi.namesByIds(anyCollection())).thenReturn(Map.of(1, "Básico", 2, "Estándar"));

        ConsumptionReport report = service.consumptionByPlanAndType(NOVEMBER);

        verify(consumptionApi).byPlanAndType(Instant.parse("2026-11-01T05:00:00Z"), Instant.parse("2026-12-01T05:00:00Z"));
        assertEquals(2, report.rows().size());
        assertEquals("Básico", report.rows().get(0).planName());
        assertEquals("Película", report.rows().get(0).typeLabel());
        assertEquals("Serie", report.rows().get(1).typeLabel());
        assertEquals(0, new BigDecimal("420.50").compareTo(report.totalMinutes()));
        assertTrue(report.hasData());
    }

    @Test
    void unknownPlanKeepsItsIdInsteadOfFailing() {
        when(consumptionApi.byPlanAndType(NOVEMBER.startInclusive(), NOVEMBER.endExclusive())).thenReturn(List.of(
                new ConsumptionRow(9, ContentType.DOCUMENTARY, new BigDecimal("10.00"), 1)));
        when(planApi.namesByIds(anyCollection())).thenReturn(Map.of());

        assertEquals("Plan 9", service.consumptionByPlanAndType(NOVEMBER).rows().getFirst().planName());
    }

    @Test
    void emptyPeriodHasNoData() {
        when(consumptionApi.byPlanAndType(NOVEMBER.startInclusive(), NOVEMBER.endExclusive())).thenReturn(List.of());
        when(planApi.namesByIds(anyCollection())).thenReturn(Map.of());

        ConsumptionReport report = service.consumptionByPlanAndType(NOVEMBER);

        assertFalse(report.hasData());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.totalMinutes()));
    }

    @Test
    void csvHasOneLinePerPlanAndTypePlusTotal() {
        when(consumptionApi.byPlanAndType(NOVEMBER.startInclusive(), NOVEMBER.endExclusive())).thenReturn(List.of(
                new ConsumptionRow(1, ContentType.MOVIE, new BigDecimal("120.50"), 2),
                new ConsumptionRow(2, ContentType.DOCUMENTARY, new BigDecimal("55.00"), 1)));
        when(planApi.namesByIds(anyCollection())).thenReturn(Map.of(1, "Básico", 2, "Estándar"));

        byte[] bytes = service.consumptionByPlanAndTypeCsv(NOVEMBER);
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8); // sin BOM

        assertEquals("""
                Plan,Tipo de contenido,Minutos consumidos,Perfiles distintos\r
                Básico,Película,120.50,2\r
                Estándar,Documental,55.00,1\r
                TOTAL,,175.50,\r
                """, csv);
        assertEquals("consumo-por-plan_2026-11-01_2026-11-30.csv", ConsumptionReportService.csvFileName(NOVEMBER));
    }
}
