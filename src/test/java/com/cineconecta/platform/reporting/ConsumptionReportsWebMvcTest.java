package com.cineconecta.platform.reporting;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.reporting.internal.service.ConsumptionReport;
import com.cineconecta.platform.reporting.internal.service.ConsumptionReportRow;
import com.cineconecta.platform.reporting.internal.service.ConsumptionReportService;
import com.cineconecta.platform.reporting.internal.service.ReportPeriod;
import com.cineconecta.platform.reporting.internal.web.ConsumptionReportsController;
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

@WebMvcTest(ConsumptionReportsController.class)
@Import({com.cineconecta.platform.shared.config.SharedConfig.class,
        com.cineconecta.platform.shared.PermitAllSecurityTestConfig.class})
class ConsumptionReportsWebMvcTest {

    private static final ReportPeriod NOVEMBER =
            new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsumptionReportService consumptionReports;
    @MockitoBean
    private Clock clock;

    @Test
    void rendersConsumptionReportForTheRequestedPeriod() throws Exception {
        when(consumptionReports.consumptionByPlanAndType(NOVEMBER)).thenReturn(new ConsumptionReport(NOVEMBER,
                List.of(new ConsumptionReportRow(2, "Estándar", ContentType.SERIES, new BigDecimal("1234.50"), 3)),
                new BigDecimal("1234.50")));

        mockMvc.perform(get("/admin/reports/consumption").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(view().name("reporting/consumption"))
                .andExpect(content().string(containsString("Estándar")))
                .andExpect(content().string(containsString("Serie")))
                .andExpect(content().string(containsString("1.234,50")))
                .andExpect(content().string(containsString("/admin/reports/consumption.csv?from=2026-11-01&amp;to=2026-11-30")));
    }

    @Test
    void defaultsToCurrentMonthWhenNoDatesAreGiven() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-26T15:00:00Z"));
        ReportPeriod expected = new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 26));
        when(consumptionReports.consumptionByPlanAndType(expected)).thenReturn(
                new ConsumptionReport(expected, List.of(), BigDecimal.ZERO));

        mockMvc.perform(get("/admin/reports/consumption"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("from", LocalDate.of(2026, 11, 1)))
                .andExpect(content().string(containsString("No hay reproducciones registradas")));
    }

    @Test
    void showsMessageAndSkipsQueryWhenPeriodIsInverted() throws Exception {
        mockMvc.perform(get("/admin/reports/consumption").param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no puede ser posterior")));

        verify(consumptionReports, never()).consumptionByPlanAndType(any());
    }

    @Test
    void downloadsCsvAsAttachment() throws Exception {
        byte[] csv = "﻿Plan,Tipo de contenido,Minutos consumidos,Perfiles distintos\r\n".getBytes(StandardCharsets.UTF_8);
        when(consumptionReports.consumptionByPlanAndTypeCsv(NOVEMBER)).thenReturn(csv);

        mockMvc.perform(get("/admin/reports/consumption.csv").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("consumo-por-plan_2026-11-01_2026-11-30.csv")))
                .andExpect(content().bytes(csv));
    }

    @Test
    void invalidPeriodOnDownloadReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/admin/reports/consumption.csv").param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("no puede ser posterior")));
    }
}
