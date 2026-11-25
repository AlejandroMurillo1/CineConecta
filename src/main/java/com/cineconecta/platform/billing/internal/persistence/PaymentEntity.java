package com.cineconecta.platform.billing.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "payments", schema = "cineconecta")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer paymentId;

    @Column(nullable = false, updatable = false, unique = true)
    private Integer subscriptionId;

    @Column(nullable = false, updatable = false)
    private Instant paidAt;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 40, updatable = false, unique = true)
    private String reference;

    protected PaymentEntity() {
    }

    public PaymentEntity(Integer subscriptionId, Instant paidAt, BigDecimal amount, String reference) {
        this.subscriptionId = subscriptionId;
        this.paidAt = paidAt;
        this.amount = amount;
        this.reference = reference;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }
}
