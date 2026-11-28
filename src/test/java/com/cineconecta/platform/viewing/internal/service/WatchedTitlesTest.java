package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.catalog.PlaybackKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void firstUnwatchedEpisodeFollowsSeasonOrder() {
        var seasons = List.of(List.of("ep-1", "ep-2"), List.of("ep-3", "ep-4"));

        assertEquals("ep-1", WatchedTitles.NONE.firstUnwatchedEpisode(seasons));
        // Solo vio el episodio 3: el siguiente sigue siendo el primero que le falta.
        assertEquals("ep-1", new WatchedTitles(List.of(new PlaybackKey("s", "ep-3"))).firstUnwatchedEpisode(seasons));
        assertEquals("ep-3", new WatchedTitles(List.of(
                new PlaybackKey("s", "ep-1"), new PlaybackKey("s", "ep-2"))).firstUnwatchedEpisode(seasons));
    }

    @Test
    void firstUnwatchedEpisodeIsNullWhenEverythingWasSeenOrThereAreNoEpisodes() {
        var seasons = List.of(List.of("ep-1"), List.of("ep-2"));
        var all = new WatchedTitles(List.of(new PlaybackKey("s", "ep-1"), new PlaybackKey("s", "ep-2")));

        assertNull(all.firstUnwatchedEpisode(seasons));
        assertNull(WatchedTitles.NONE.firstUnwatchedEpisode(List.of()));
    }
}
