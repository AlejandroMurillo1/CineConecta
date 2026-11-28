package com.cineconecta.platform.reporting;

import com.cineconecta.platform.billing.PlanRevenueRow;
import com.cineconecta.platform.reporting.internal.service.ReportPeriod;
import com.cineconecta.platform.reporting.internal.service.RevenueReport;
import com.cineconecta.platform.reporting.internal.service.RevenueReportService;
import com.cineconecta.platform.reporting.internal.web.ReportsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReportsController.class)
@Import({com.cineconecta.platform.shared.config.SharedConfig.class,
        com.cineconecta.platform.shared.PermitAllSecurityTestConfig.class})
class ReportsWebMvcTest {

    private static final ReportPeriod NOVEMBER =
            new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RevenueReportService revenueReports;
    @MockitoBean
    private Clock clock;

    @Test
    void rendersRevenueReportForTheRequestedPeriod() throws Exception {
        when(revenueReports.revenueByPlan(NOVEMBER)).thenReturn(new RevenueReport(NOVEMBER,
                List.of(new PlanRevenueRow(2, "Estándar", 3, new BigDecimal("80700.00"))),
                3, new BigDecimal("80700.00")));

        mockMvc.perform(get("/admin/reports/revenue").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(view().name("reporting/revenue"))
                .andExpect(content().string(containsString("Estándar")))
                .andExpect(content().string(containsString("80.700")))
                .andExpect(content().string(containsString("/admin/reports/revenue.csv?from=2026-11-01&amp;to=2026-11-30")));
    }

    @Test
    void defaultsToCurrentMonthWhenNoDatesAreGiven() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-26T15:00:00Z"));
        ReportPeriod expected = new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 26));
        when(revenueReports.revenueByPlan(expected)).thenReturn(
                new RevenueReport(expected, List.of(), 0, BigDecimal.ZERO));

        mockMvc.perform(get("/admin/reports/revenue"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("from", LocalDate.of(2026, 11, 1)))
                .andExpect(content().string(containsString("No hay pagos registrados")));
    }

    @Test
    void showsMessageAndSkipsQueryWhenPeriodIsInverted() throws Exception {
        mockMvc.perform(get("/admin/reports/revenue").param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no puede ser posterior")));

        verify(revenueReports, never()).revenueByPlan(any());
    }

    @Test
    void downloadsCsvAsAttachment() throws Exception {
        byte[] csv = "﻿Plan,Suscripciones adquiridas,Total recaudado (COP)\r\n".getBytes(StandardCharsets.UTF_8);
        when(revenueReports.revenueByPlanCsv(NOVEMBER)).thenReturn(csv);

        mockMvc.perform(get("/admin/reports/revenue.csv").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("ingresos-por-plan_2026-11-01_2026-11-30.csv")))
                .andExpect(content().bytes(csv));
    }

    @Test
    void invalidPeriodOnDownloadReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/admin/reports/revenue.csv").param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("no puede ser posterior")));
    }
}
