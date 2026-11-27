package com.cineconecta.platform.reporting;

import com.cineconecta.platform.billing.PlanRevenueRow;
import com.cineconecta.platform.billing.RevenueApi;
import com.cineconecta.platform.reporting.internal.service.ReportPeriod;
import com.cineconecta.platform.reporting.internal.service.RevenueReport;
import com.cineconecta.platform.reporting.internal.service.RevenueReportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevenueReportServiceTest {

    private static final ReportPeriod NOVEMBER =
            new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    private final RevenueApi revenueApi = mock(RevenueApi.class);
    private final RevenueReportService service = new RevenueReportService(revenueApi);

    @Test
    void asksBillingForTheHalfOpenRangeAndAddsTotals() {
        when(revenueApi.revenueByPlan(NOVEMBER.startInclusive(), NOVEMBER.endExclusive())).thenReturn(List.of(
                new PlanRevenueRow(1, "Básico", 2, new BigDecimal("33800.00")),
                new PlanRevenueRow(2, "Estándar", 3, new BigDecimal("80700.00")),
                new PlanRevenueRow(3, "Premium", 0, BigDecimal.ZERO)));

        RevenueReport report = service.revenueByPlan(NOVEMBER);

        verify(revenueApi).revenueByPlan(Instant.parse("2026-11-01T05:00:00Z"), Instant.parse("2026-12-01T05:00:00Z"));
        assertEquals(3, report.rows().size());
        assertEquals(5, report.totalSubscriptions());
        assertEquals(0, new BigDecimal("114500.00").compareTo(report.totalRevenue()));
        assertTrue(report.hasData());
    }

    @Test
    void emptyPeriodHasZeroTotals() {
        when(revenueApi.revenueByPlan(NOVEMBER.startInclusive(), NOVEMBER.endExclusive()))
                .thenReturn(List.of(new PlanRevenueRow(1, "Básico", 0, BigDecimal.ZERO)));

        RevenueReport report = service.revenueByPlan(NOVEMBER);

        assertEquals(0, report.totalSubscriptions());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.totalRevenue()));
        assertFalse(report.hasData());
    }

    @Test
    void csvHasOneLinePerPlanPlusTotal() {
        when(revenueApi.revenueByPlan(NOVEMBER.startInclusive(), NOVEMBER.endExclusive())).thenReturn(List.of(
                new PlanRevenueRow(1, "Básico", 2, new BigDecimal("33800.00")),
                new PlanRevenueRow(2, "Estándar", 3, new BigDecimal("80700.00"))));

        byte[] bytes = service.revenueByPlanCsv(NOVEMBER);
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8); // sin BOM

        assertEquals("""
                Plan,Suscripciones adquiridas,Total recaudado (COP)\r
                Básico,2,33800.00\r
                Estándar,3,80700.00\r
                TOTAL,5,114500.00\r
                """, csv);
        assertEquals("ingresos-por-plan_2026-11-01_2026-11-30.csv", RevenueReportService.csvFileName(NOVEMBER));
    }
}
