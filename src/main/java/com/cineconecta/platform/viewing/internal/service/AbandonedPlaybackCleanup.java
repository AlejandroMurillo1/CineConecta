package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.viewing.internal.persistence.PlaybackSessionStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AbandonedPlaybackCleanup {
    private final PlaybackSessionStore store;
    private final Clock clock;

    AbandonedPlaybackCleanup(PlaybackSessionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    void closeInactiveSessions() {
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        store.closeAbandoned(now, now.minus(PlaybackSession.INACTIVITY_LIMIT));
    }
}
