package com.cineconecta.platform.billing.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Integer> {

    Optional<PaymentEntity> findBySubscriptionId(int subscriptionId);

    List<PaymentEntity> findBySubscriptionIdIn(Collection<Integer> subscriptionIds);
}
