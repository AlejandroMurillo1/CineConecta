package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.billing.SubscriptionApi;
import com.cineconecta.platform.billing.SubscriptionView;
import com.cineconecta.platform.shared.CurrentAccountProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class ViewingAccess {
    private final CurrentAccountProvider currentAccount;
    private final ProfileApi profiles;
    private final SubscriptionApi subscriptions;
    private final Clock clock;

    public ViewingAccess(CurrentAccountProvider currentAccount, ProfileApi profiles,
                         SubscriptionApi subscriptions, Clock clock) {
        this.currentAccount = currentAccount;
        this.profiles = profiles;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    public EligibleProfile requireEligibleProfile(int profileId) {
        int accountId = currentAccount.currentAccountId().orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("Inicia sesión para continuar"));
        ProfileView profile = profiles.find(profileId)
                .filter(candidate -> candidate.accountId() == accountId && candidate.active())
                .orElseThrow(() -> new AccessDeniedException("El perfil no está disponible"));
        SubscriptionView subscription = subscriptions.current(accountId, clock.instant())
                .orElseThrow(() -> new AccessDeniedException("Necesitas una suscripción vigente"));
        return new EligibleProfile(profile, subscription);
    }

    public record EligibleProfile(ProfileView profile, SubscriptionView subscription) {}
}
