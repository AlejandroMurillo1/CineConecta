package com.cineconecta.platform.billing.internal.service;

import com.cineconecta.platform.billing.SubscriptionView;

import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionHistoryItem(
        SubscriptionView subscription,
        boolean active,
        BigDecimal amountPaid,
        Instant paidAt,
        String paymentReference
) {
}
