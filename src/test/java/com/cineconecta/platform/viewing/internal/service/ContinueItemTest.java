package com.cineconecta.platform.viewing.internal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Cálculos que muestran las tarjetas de "Continuar viendo". */
class ContinueItemTest {

    private static PlaybackService.ContinueItem item(Integer season, Integer episode, int position, int duration) {
        return new PlaybackService.ContinueItem("s", "c", "Título", null, season, episode, position, duration, null);
    }

    @Test
    void progressIsThePercentageWatched() {
        assertEquals(50, item(null, null, 1410, 2820).progressPercent());
        assertEquals(0, item(null, null, 10, 0).progressPercent());
        assertEquals(100, item(null, null, 9000, 6000).progressPercent());
    }

    @Test
    void remainingMinutesRoundUpAndAreAtLeastOne() {
        assertEquals(24, item(null, null, 1410, 2820).remainingMinutes());
        assertEquals(1, item(null, null, 2819, 2820).remainingMinutes());
        assertEquals(1, item(null, null, 3000, 2820).remainingMinutes());
    }

    @Test
    void episodeLabelOnlyForSeries() {
        assertEquals("T1 · E2", item(1, 2, 10, 100).episodeLabel());
        assertNull(item(null, null, 10, 100).episodeLabel());
    }
}
