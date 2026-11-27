package com.cineconecta.platform.billing.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "subscriptions", schema = "cineconecta")
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer subscriptionId;

    @Column(nullable = false, updatable = false)
    private Integer accountId;

    @Column(nullable = false, updatable = false)
    private Integer planId;

    @Column(nullable = false, updatable = false)
    private Instant startsAt;

    @Column(nullable = false, updatable = false)
    private Instant endsAt;

    protected SubscriptionEntity() {
    }

    public SubscriptionEntity(Integer accountId, Integer planId, Instant startsAt, Instant endsAt) {
        this.accountId = accountId;
        this.planId = planId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Integer getPlanId() {
        return planId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }
}
