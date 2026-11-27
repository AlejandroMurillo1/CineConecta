package com.cineconecta.platform.reporting.internal.web;

import com.cineconecta.platform.reporting.internal.service.InvalidReportPeriodException;
import com.cineconecta.platform.reporting.internal.service.ReportPeriod;
import com.cineconecta.platform.reporting.internal.service.RevenueReportService;
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
 * Informes de la administración.
 *
 * <p>Seguridad: las rutas {@code /admin/**} deben quedar protegidas por la cadena de seguridad de
 * administradores (rol ADMIN) del módulo backoffice. Mientras esa cadena no exista, la cadena
 * de respaldo de {@code shared} deja pasar todas las solicitudes.</p>
 */
@Controller
@RequestMapping("/admin/reports")
public class ReportsController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RevenueReportService revenueReports;
    private final Clock clock;

    public ReportsController(RevenueReportService revenueReports, Clock clock) {
        this.revenueReports = revenueReports;
        this.clock = clock;
    }

    /** RF-33: ingresos por plan, con filtro de fechas (por defecto, el mes en curso). */
    @GetMapping("/revenue")
    public String revenue(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        try {
            ReportPeriod period = ReportPeriod.resolve(from, to, clock);
            model.addAttribute("report", revenueReports.revenueByPlan(period));
            model.addAttribute("from", period.from());
            model.addAttribute("to", period.to());
        } catch (InvalidReportPeriodException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("from", from);
            model.addAttribute("to", to);
        }
        return "reporting/revenue";
    }

    /** RF-39: exportación a CSV del informe de ingresos, con el mismo filtro. */
    @GetMapping("/revenue.csv")
    public ResponseEntity<byte[]> revenueCsv(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportPeriod period = ReportPeriod.resolve(from, to, clock);
        byte[] csv = revenueReports.revenueByPlanCsv(period);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(RevenueReportService.csvFileName(period), StandardCharsets.UTF_8)
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
