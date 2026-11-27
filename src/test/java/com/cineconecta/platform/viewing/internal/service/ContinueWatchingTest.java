package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.billing.PlanSummaryView;
import com.cineconecta.platform.billing.SubscriptionApi;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.ICatalogApi;
import com.cineconecta.platform.catalog.PlayableRef;
import com.cineconecta.platform.viewing.internal.persistence.PlaybackSessionStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContinueWatchingTest {
    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");
    private static final String ID = "65f1a2b3c4d5e6f708192a01";
    private final ICatalogApi catalog = mock(ICatalogApi.class);
    private final SubscriptionApi billing = mock(SubscriptionApi.class);
    private final PlaybackSessionStore store = mock(PlaybackSessionStore.class);
    private final ViewingAccess access = mock(ViewingAccess.class);
    private final PlaybackService service = new PlaybackService(access, catalog, billing, store,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsOnlyAvailableUnfinishedTitlesInActivityOrder() {
        allowProfile();
        var older = session(ID, 10, NOW.minusSeconds(100));
        var newer = session("65f1a2b3c4d5e6f708192a02", 20, NOW.minusSeconds(30));
        var complete = session("65f1a2b3c4d5e6f708192a03", 90, NOW.minusSeconds(20));
        var withdrawn = session("65f1a2b3c4d5e6f708192a04", 35, NOW.minusSeconds(10));
        when(store.latestPerTitle(7, 42)).thenReturn(List.of(older, newer, complete, withdrawn));
        when(catalog.findPlayables(List.of(withdrawn.key(), newer.key(), older.key())))
                .thenReturn(Map.of(older.key(), title(older), newer.key(), title(newer)));

        assertEquals(List.of(20, 10), service.continueWatching(42).stream()
                .map(PlaybackService.ContinueItem::position).toList());
    }

    @Test
    void resumesClosedSessionAtSavedPositionWithoutCopyingConsumption() {
        allowProfile();
        var previous = session(ID, 35, NOW.minusSeconds(100));
        when(store.find(ID, 7, 42)).thenReturn(Optional.of(previous));
        when(catalog.findPlayable(ID, null)).thenReturn(Optional.of(title(previous)));
        when(billing.planAt(7, NOW)).thenReturn(Optional.of(new PlanSummaryView(9, "Estándar",
                BigDecimal.ONE, 3, true)));

        var resumed = service.resume(42, ID);

        assertNotEquals(previous.id(), resumed.id());
        assertEquals(35, resumed.lastPosition());
        assertEquals(0, resumed.watchedSeconds());
        assertEquals(9, resumed.planIdAtStart());
        verify(store).insert(resumed);
    }

    private void allowProfile() {
        when(access.requireEligibleProfile(42)).thenReturn(new ViewingAccess.EligibleProfile(
                new ProfileView(42, 7, "Principal", 13, true), null));
    }

    private PlaybackSession session(String id, int position, Instant activity) {
        return new PlaybackSession(id, 7, 42, id, null, ContentType.MOVIE, 3, 100,
                NOW.minusSeconds(500), null, activity, position, position, false, 1, activity);
    }

    private PlayableRef title(PlaybackSession session) {
        return new PlayableRef(session.contentId(), null, ContentType.MOVIE, "La película",
                null, null, null, 100, 12, null);
    }
}
