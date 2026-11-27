package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.billing.SubscriptionApi;
import com.cineconecta.platform.billing.SubscriptionView;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ViewingAccessTest {
    private static final Instant NOW = Instant.parse("2026-11-25T15:00:00Z");
    private final ProfileApi profiles = mock(ProfileApi.class);
    private final SubscriptionApi subscriptions = mock(SubscriptionApi.class);
    private final ViewingAccess access = new ViewingAccess(
            () -> OptionalInt.of(7), profiles, subscriptions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void rejectsAnonymousAccountsBeforeReadingProfile() {
        var anonymous = new ViewingAccess(OptionalInt::empty, profiles, subscriptions, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> anonymous.requireEligibleProfile(42));
        verifyNoInteractions(profiles, subscriptions);
    }

    @Test
    void rejectsProfileFromAnotherAccount() {
        when(profiles.find(42)).thenReturn(Optional.of(new ProfileView(42, 8, "Otro", 18, true)));

        assertThrows(AccessDeniedException.class, () -> access.requireEligibleProfile(42));
        verifyNoInteractions(subscriptions);
    }

    @Test
    void rejectsInactiveProfile() {
        when(profiles.find(42)).thenReturn(Optional.of(new ProfileView(42, 7, "Alex", 13, false)));

        assertThrows(AccessDeniedException.class, () -> access.requireEligibleProfile(42));
        verifyNoInteractions(subscriptions);
    }

    @Test
    void rejectsAccountWithoutCurrentSubscription() {
        when(profiles.find(42)).thenReturn(Optional.of(new ProfileView(42, 7, "Alex", 13, true)));
        when(subscriptions.current(7, NOW)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> access.requireEligibleProfile(42));
    }

    @Test
    void returnsProfileRatingAndCurrentPlanForEligibleAccount() {
        when(profiles.find(42)).thenReturn(Optional.of(new ProfileView(42, 7, "Alex", 13, true)));
        when(subscriptions.current(7, NOW)).thenReturn(Optional.of(
                new SubscriptionView(11, 7, 3, "Familiar", 4, NOW.minusSeconds(60), NOW.plusSeconds(86400))));

        var result = access.requireEligibleProfile(42);

        assertEquals(13, result.profile().maxAgeRating());
        assertEquals(3, result.subscription().planId());
    }
}
