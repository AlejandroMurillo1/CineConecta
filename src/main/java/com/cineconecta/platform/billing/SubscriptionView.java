package com.cineconecta.platform.billing;

import java.time.Instant;

/**
 * Vista pública de una suscripción con vigencia en el intervalo semiabierto [startsAt, endsAt).
 */
public record SubscriptionView(
        int subscriptionId,
        int accountId,
        int planId,
        String planName,
        int maxProfiles,
        Instant startsAt,
        Instant endsAt
) {
}
