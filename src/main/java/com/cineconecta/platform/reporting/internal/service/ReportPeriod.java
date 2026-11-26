package com.cineconecta.platform.reporting.internal.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Período de un informe en días de calendario de Colombia, con ambos extremos incluidos:
 * del 1 al 30 de noviembre significa desde el 1 a las 00:00 hasta el 30 a las 23:59:59 (hora Colombia).
 *
 * <p>Las bases guardan instantes en UTC; {@link #startInclusive()} y {@link #endExclusive()}
 * convierten el período al rango semiabierto {@code [inicio, fin)} que esperan las consultas.</p>
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    /** Límite de amplitud para evitar consultas desproporcionadas por error de digitación. */
    static final long MAX_DAYS = 731;

    public ReportPeriod {
        if (from == null || to == null) {
            throw new InvalidReportPeriodException("Indica la fecha inicial y la fecha final del período.");
        }
        if (from.isAfter(to)) {
            throw new InvalidReportPeriodException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidReportPeriodException("El período no puede superar dos años.");
        }
    }

    /** Desde el primer día del mes en curso hasta hoy (hora Colombia). */
    public static ReportPeriod currentMonth(Clock clock) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        return new ReportPeriod(today.withDayOfMonth(1), today);
    }

    /** Usa las fechas recibidas; si no llega ninguna, el mes en curso. */
    public static ReportPeriod resolve(LocalDate from, LocalDate to, Clock clock) {
        if (from == null && to == null) {
            return currentMonth(clock);
        }
        return new ReportPeriod(from, to);
    }

    /** Primer instante del período (incluido). */
    public Instant startInclusive() {
        return from.atStartOfDay(ZONE).toInstant();
    }

    /** Primer instante posterior al período (excluido). */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
