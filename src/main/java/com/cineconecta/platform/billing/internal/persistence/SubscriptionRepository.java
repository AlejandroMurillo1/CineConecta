package com.cineconecta.platform.billing.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Integer> {

    @Query("""
            SELECT s
            FROM SubscriptionEntity s
            WHERE s.accountId = :accountId
              AND s.startsAt <= :at
              AND :at < s.endsAt
            """)
    Optional<SubscriptionEntity> findActiveAt(@Param("accountId") int accountId, @Param("at") Instant at);

    List<SubscriptionEntity> findByAccountIdOrderByStartsAtDesc(int accountId);

    @Query("""
            SELECT s.planId, COUNT(s)
            FROM SubscriptionEntity s
            WHERE s.startsAt <= :at
              AND :at < s.endsAt
            GROUP BY s.planId
            """)
    List<Object[]> countActiveByPlanAt(@Param("at") Instant at);
}
