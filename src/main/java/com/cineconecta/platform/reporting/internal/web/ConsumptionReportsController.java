package com.cineconecta.platform.reporting.internal.web;

import com.cineconecta.platform.reporting.internal.service.ConsumptionReportService;
import com.cineconecta.platform.reporting.internal.service.InvalidReportPeriodException;
import com.cineconecta.platform.reporting.internal.service.ReportPeriod;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Informe de consumo por plan y tipo de contenido. Queda bajo {@code /admin/**}, protegido por la
 * cadena de seguridad de administradores (rol ADMIN) del módulo backoffice.
 */
@Controller
@RequestMapping("/admin/reports")
public class ConsumptionReportsController {

    private final ConsumptionReportService consumptionReports;
    private final Clock clock;

    public ConsumptionReportsController(ConsumptionReportService consumptionReports, Clock clock) {
        this.consumptionReports = consumptionReports;
        this.clock = clock;
    }

    /** Consumo por plan y tipo, con filtro de fechas (por defecto, el mes en curso). */
    @GetMapping("/consumption")
    public String consumption(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        try {
            ReportPeriod period = ReportPeriod.resolve(from, to, clock);
            model.addAttribute("report", consumptionReports.consumptionByPlanAndType(period));
            model.addAttribute("from", period.from());
            model.addAttribute("to", period.to());
        } catch (InvalidReportPeriodException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("from", from);
            model.addAttribute("to", to);
        }
        return "reporting/consumption";
    }

    /** Exportación a CSV del informe de consumo, con el mismo filtro. */
    @GetMapping("/consumption.csv")
    public ResponseEntity<byte[]> consumptionCsv(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportPeriod period = ReportPeriod.resolve(from, to, clock);
        byte[] csv = consumptionReports.consumptionByPlanAndTypeCsv(period);
        return ResponseEntity.ok()
                .contentType(ReportsController.TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(ConsumptionReportService.csvFileName(period), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(csv);
    }

    /** Un período inválido en la descarga devuelve 400 con el motivo, en lugar de un archivo vacío. */
    @ExceptionHandler(InvalidReportPeriodException.class)
    public ResponseEntity<String> invalidPeriod(InvalidReportPeriodException ex) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(ex.getMessage());
    }
}
