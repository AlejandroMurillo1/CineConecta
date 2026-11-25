package com.cineconecta.platform.billing.internal.service;

import com.cineconecta.platform.accounts.ProfileEntitlements;
import com.cineconecta.platform.billing.SubscriptionApi;
import com.cineconecta.platform.billing.SubscriptionView;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Implementación de {@link ProfileEntitlements} en billing para evitar ciclo accounts ↔ billing.
 */
@Component
public class ProfileEntitlementsAdapter implements ProfileEntitlements {

    private final SubscriptionApi subscriptionApi;
    private final Clock clock;

    public ProfileEntitlementsAdapter(SubscriptionApi subscriptionApi, Clock clock) {
        this.subscriptionApi = subscriptionApi;
        this.clock = clock;
    }

    @Override
    public int maxProfilesFor(int accountId) {
        return subscriptionApi.current(accountId, clock.instant())
                .map(SubscriptionView::maxProfiles)
                .orElse(0);
    }
}
