package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.catalog.ContentType;

import java.time.Duration;
import java.time.Instant;

public record PlaybackSession(
        String id, int accountId, int profileId, String contentId, String episodeId,
        ContentType type, int planIdAtStart, int durationSeconds, Instant startedAt,
        Instant playingSince, Instant endedAt, int watchedSeconds, int lastPosition,
        boolean finished, int revision
) {
    public enum Action { PAUSE, RESUME, SEEK, FINISH }

    public int positionAt(Instant now) {
        return lastPosition + elapsedSeconds(now);
    }

    public PlaybackSession apply(Action action, Integer target, Instant now) {
        if (endedAt != null) {
            return this;
        }

        int elapsed = elapsedSeconds(now);
        int position = lastPosition + elapsed;
        int watched = (int) Math.min(Integer.MAX_VALUE, (long) watchedSeconds + elapsed);
        Instant nextPlayingSince = playingSince;
        Instant closedAt = null;

        if (playingSince != null && position == durationSeconds) {
            closedAt = playingSince.plusSeconds(elapsed);
            nextPlayingSince = null;
        } else {
            switch (action) {
                case PAUSE -> nextPlayingSince = null;
                case RESUME -> nextPlayingSince = playingSince == null ? now : playingSince.plusSeconds(elapsed);
                case SEEK -> {
                    if (target == null || target < 0 || target > durationSeconds) {
                        throw new IllegalArgumentException("La posición está fuera de la duración del título.");
                    }
                    position = target;
                    nextPlayingSince = playingSince == null ? null : now;
                }
                case FINISH -> {
                    closedAt = now;
                    nextPlayingSince = null;
                }
            }
        }

        return new PlaybackSession(id, accountId, profileId, contentId, episodeId, type, planIdAtStart,
                durationSeconds, startedAt, nextPlayingSince, closedAt, watched, position,
                action == Action.FINISH, revision + 1);
    }

    private int elapsedSeconds(Instant now) {
        if (playingSince == null) {
            return 0;
        }
        long elapsed = Duration.between(playingSince, now).toSeconds();
        return (int) Math.max(0, Math.min(elapsed, durationSeconds - lastPosition));
    }
}
