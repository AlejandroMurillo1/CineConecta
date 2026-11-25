package com.cineconecta.platform.billing.internal.service;

import com.cineconecta.platform.billing.SubscriptionView;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseResult(
        SubscriptionView subscription,
        int paymentId,
        BigDecimal amount,
        Instant paidAt,
        String reference
) {
}
