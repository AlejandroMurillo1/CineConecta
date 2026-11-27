package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession.Action;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackSessionTest {
    private final Instant start = Instant.parse("2026-11-26T15:00:00Z");
    private final PlaybackSession session = new PlaybackSession("session", 7, 42, "title", null,
            ContentType.MOVIE, 3, 200, start, start, null, 0, 0, false, 0, start);

    @Test
    void seekingChangesPositionWithoutAddingTheJumpToWatchedTime() {
        var advanced = session.apply(Action.SEEK, 120, start.plusSeconds(20));

        assertEquals(120, advanced.lastPosition());
        assertEquals(20, advanced.watchedSeconds());
    }

    @Test
    void pauseTimeIsExcludedAfterResuming() {
        var paused = session.apply(Action.PAUSE, null, start.plusSeconds(10));
        var resumed = paused.apply(Action.RESUME, null, start.plusSeconds(70));
        var finished = resumed.apply(Action.FINISH, null, start.plusSeconds(75));

        assertEquals(15, finished.watchedSeconds());
        assertEquals(15, finished.lastPosition());
        assertEquals(start.plusSeconds(75), finished.endedAt());
        assertTrue(finished.finished());
        assertNull(finished.playingSince());
    }

    @Test
    void closesAnAbandonedSessionAtItsLastConfirmedPosition() {
        var checkpoint = session.apply(Action.CHECKPOINT, null, start.plusSeconds(10));
        var closed = checkpoint.closeIfAbandoned(start.plusSeconds(70));

        assertSame(checkpoint, checkpoint.closeIfAbandoned(start.plusSeconds(69)));
        assertEquals(start.plusSeconds(10), closed.endedAt());
        assertEquals(10, closed.watchedSeconds());
        assertEquals(10, closed.lastPosition());
        assertFalse(closed.finished());
        assertNull(closed.playingSince());
    }

    @Test
    void completionStartsAtNinetyPercentOrWhenUserFinishes() {
        assertFalse(session.apply(Action.SEEK, 179, start).completed());
        assertTrue(session.apply(Action.SEEK, 180, start).completed());
        assertTrue(session.apply(Action.FINISH, null, start).completed());
    }

    @Test
    void watchingIsCappedAtTheEndOfTheTitle() {
        var ended = session.apply(Action.PAUSE, null, start.plusSeconds(500));

        assertEquals(200, ended.watchedSeconds());
        assertEquals(200, ended.lastPosition());
        assertEquals(start.plusSeconds(200), ended.endedAt());
        assertFalse(ended.finished());
    }
}
