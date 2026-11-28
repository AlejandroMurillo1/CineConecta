package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.billing.SubscriptionView;
import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentSummary;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.ICatalogApi;
import com.cineconecta.platform.viewing.internal.persistence.MyListStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MyListServiceTest {
    private static final int PROFILE_ID = 42;
    private static final String TITLE_ID = "6740a1f0c3b2a1d4e5f60001";
    private final ViewingAccess access = mock(ViewingAccess.class);
    private final ICatalogApi catalog = mock(ICatalogApi.class);
    private final MyListStore store = mock(MyListStore.class);
    private final MyListService service = new MyListService(access, catalog, store);

    @BeforeEach
    void setUpProfile() {
        when(access.requireEligibleProfile(PROFILE_ID)).thenReturn(new ViewingAccess.EligibleProfile(
                new ProfileView(PROFILE_ID, 7, "Alex", 13, true),
                new SubscriptionView(11, 7, 3, "Familiar", 4, Instant.EPOCH, Instant.EPOCH.plusSeconds(86400))));
    }

    @Test
    void refusesToAddTitleAboveProfileAgeLimit() {
        setTitle(ContentStatus.PUBLISHED, 16);

        assertThrows(IllegalArgumentException.class, () -> service.add(PROFILE_ID, TITLE_ID));
        verifyNoInteractions(store);
    }

    @Test
    void acceptsPublishedTitleAtProfileAgeLimit() {
        setTitle(ContentStatus.PUBLISHED, 13);

        service.add(PROFILE_ID, TITLE_ID);

        verify(store).add(PROFILE_ID, TITLE_ID);
    }

    @Test
    void displaysRetiredTitleAndAllowsItsRemoval() {
        setTitle(ContentStatus.RETIRED, 13);
        when(store.findIds(PROFILE_ID)).thenReturn(List.of(TITLE_ID));

        var items = service.list(PROFILE_ID);
        assertEquals(1, items.size());
        assertEquals("Retirado", items.get(0).status());

        service.remove(PROFILE_ID, TITLE_ID);
        verify(store).remove(PROFILE_ID, TITLE_ID);
    }

    @Test
    void listCarriesPosterAndTypeAndMarksTitlesAboveTheRatingAsUnavailable() {
        String adult = "6740a1f0c3b2a1d4e5f60002";
        when(store.findIds(PROFILE_ID)).thenReturn(List.of(TITLE_ID, adult));
        when(catalog.findSummaries(List.of(TITLE_ID, adult))).thenReturn(Map.of(
                TITLE_ID, new ContentSummary(TITLE_ID, "Río Abajo", ContentType.SERIES, 13,
                        ContentStatus.PUBLISHED, "/images/posters/rio.svg"),
                adult, new ContentSummary(adult, "Noche", ContentType.MOVIE, 18,
                        ContentStatus.PUBLISHED, "/images/posters/noche.svg")));

        var items = service.list(PROFILE_ID);

        assertEquals("/images/posters/rio.svg", items.get(0).posterUrl());
        assertEquals("Serie", items.get(0).typeLabel());
        assertTrue(items.get(0).available());
        assertFalse(items.get(1).available());
        assertEquals("Título no disponible", items.get(1).title());
        assertNull(items.get(1).posterUrl());
    }

    @Test
    void deniesRemovalForUnauthorizedProfile() {
        when(access.requireEligibleProfile(PROFILE_ID)).thenThrow(new AccessDeniedException("Perfil ajeno"));

        assertThrows(AccessDeniedException.class, () -> service.remove(PROFILE_ID, TITLE_ID));
        verifyNoInteractions(store);
    }

    private void setTitle(ContentStatus status, int ageRating) {
        when(catalog.findSummaries(List.of(TITLE_ID))).thenReturn(Map.of(TITLE_ID,
                new ContentSummary(TITLE_ID, "Río Abajo", ContentType.MOVIE, ageRating, status, null)));
    }
}
