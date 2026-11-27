package com.cineconecta.platform.reporting;

import com.cineconecta.platform.reporting.internal.service.InvalidReportPeriodException;
import com.cineconecta.platform.reporting.internal.service.ReportPeriod;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportPeriodTest {

    @Test
    void convertsColombianCalendarDaysToHalfOpenUtcRange() {
        ReportPeriod period = new ReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        // Colombia es UTC-5 todo el año: el 1 de noviembre 00:00 en Bogotá son las 05:00 UTC.
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), period.startInclusive());
        // El día final se incluye completo: el fin (excluido) es el 1 de diciembre 00:00 en Bogotá.
        assertEquals(Instant.parse("2026-12-01T05:00:00Z"), period.endExclusive());
    }

    @Test
    void singleDayPeriodCoversTheWholeDay() {
        ReportPeriod day = new ReportPeriod(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25));

        assertEquals(Instant.parse("2026-11-25T05:00:00Z"), day.startInclusive());
        assertEquals(Instant.parse("2026-11-26T05:00:00Z"), day.endExclusive());
    }

    @Test
    void defaultsToCurrentMonthInColombia() {
        // 1 de diciembre 03:00 UTC = 30 de noviembre 22:00 en Bogotá: el mes en curso aún es noviembre.
        Clock clock = Clock.fixed(Instant.parse("2026-12-01T03:00:00Z"), ZoneOffset.UTC);

        ReportPeriod period = ReportPeriod.resolve(null, null, clock);

        assertEquals(LocalDate.of(2026, 11, 1), period.from());
        assertEquals(LocalDate.of(2026, 11, 30), period.to());
    }

    @Test
    void rejectsInvertedMissingOrTooWidePeriods() {
        Clock clock = Clock.systemUTC();

        assertThrows(InvalidReportPeriodException.class,
                () -> new ReportPeriod(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1)));
        assertThrows(InvalidReportPeriodException.class,
                () -> ReportPeriod.resolve(LocalDate.of(2026, 11, 1), null, clock));
        assertThrows(InvalidReportPeriodException.class,
                () -> new ReportPeriod(LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1)));
    }
}
