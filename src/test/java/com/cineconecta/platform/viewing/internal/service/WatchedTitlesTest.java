package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.catalog.PlaybackKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchedTitlesTest {

    @Test
    void moviesCountWholeAndSeriesCountDistinctEpisodes() {
        var watched = new WatchedTitles(List.of(
                PlaybackKey.of("movie"),
                new PlaybackKey("series", "ep-1"),
                new PlaybackKey("series", "ep-2"),
                new PlaybackKey("series", "ep-2")));

        assertTrue(watched.content("movie"));
        assertFalse(watched.content("series"));
        assertTrue(watched.episode("ep-1"));
        assertFalse(watched.episode("ep-3"));
        assertEquals(2, watched.episodesOf("series"));
        assertEquals(0, watched.episodesOf("movie"));
    }

    @Test
    void noneHasNothing() {
        assertFalse(WatchedTitles.NONE.content("movie"));
        assertEquals(0, WatchedTitles.NONE.episodesOf("series"));
    }
}
