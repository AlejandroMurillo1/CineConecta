package com.cineconecta.platform.viewing.internal.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaybackTimeTest {

    @Test
    void formatsSecondsAsHoursMinutesSeconds() {
        assertEquals("00:00:00", PlaybackTime.format(0));
        assertEquals("00:45:00", PlaybackTime.format(2700));
        assertEquals("01:02:05", PlaybackTime.format(3725));
    }

    @Test
    void parsesHoursMinutesSecondsMinutesSecondsOrPlainSeconds() {
        assertEquals(3725, PlaybackTime.parse("01:02:05"));
        assertEquals(750, PlaybackTime.parse("12:30"));
        assertEquals(90, PlaybackTime.parse("90"));
        assertNull(PlaybackTime.parse("  "));
    }

    @Test
    void rejectsInvalidPositions() {
        assertThrows(IllegalArgumentException.class, () -> PlaybackTime.parse("00:75:00"));
        assertThrows(IllegalArgumentException.class, () -> PlaybackTime.parse("1:2:3:4"));
        assertThrows(IllegalArgumentException.class, () -> PlaybackTime.parse("abc"));
    }

    @Test
    void percentStaysBetweenZeroAndHundred() {
        assertEquals(50.0, PlaybackTime.percent(30, 60));
        assertEquals(100.0, PlaybackTime.percent(90, 60));
        assertEquals(0.0, PlaybackTime.percent(10, 0));
    }
}
