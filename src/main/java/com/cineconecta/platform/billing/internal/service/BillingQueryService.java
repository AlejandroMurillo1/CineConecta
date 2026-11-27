package com.cineconecta.platform.billing.internal.service;

import com.cineconecta.platform.billing.PlanApi;
import com.cineconecta.platform.billing.PlanRevenueRow;
import com.cineconecta.platform.billing.PlanSummaryView;
import com.cineconecta.platform.billing.RevenueApi;
import com.cineconecta.platform.billing.SubscriptionApi;
import com.cineconecta.platform.billing.SubscriptionView;
import com.cineconecta.platform.billing.internal.persistence.PaymentEntity;
import com.cineconecta.platform.billing.internal.persistence.PaymentRepository;
import com.cineconecta.platform.billing.internal.persistence.PlanEntity;
import com.cineconecta.platform.billing.internal.persistence.PlanRepository;
import com.cineconecta.platform.billing.internal.persistence.SubscriptionEntity;
import com.cineconecta.platform.billing.internal.persistence.SubscriptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación de las APIs públicas de lectura del módulo billing y del historial del titular.
 */
@Service
@Transactional(readOnly = true)
public class BillingQueryService implements SubscriptionApi, PlanApi, RevenueApi {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public BillingQueryService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            PaymentRepository paymentRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<SubscriptionView> current(int accountId, Instant at) {
        Objects.requireNonNull(at, "El instante de consulta 'at' es obligatorio");
        return subscriptionRepository.findActiveAt(accountId, at)
                .flatMap(sub -> planRepository.findById(sub.getPlanId())
                        .map(plan -> toSubscriptionView(sub, plan)));
    }

    @Override
    public Optional<PlanSummaryView> planAt(int accountId, Instant at) {
        Objects.requireNonNull(at, "El instante de consulta 'at' es obligatorio");
        return subscriptionRepository.findActiveAt(accountId, at)
                .flatMap(sub -> planRepository.findById(sub.getPlanId()))
                .map(BillingQueryService::toPlanSummaryView);
    }

    @Override
    public List<PlanSummaryView> findActivePlans() {
        return planRepository.findByIsActiveOrderByPriceAsc("Y").stream()
                .map(BillingQueryService::toPlanSummaryView)
                .toList();
    }

    @Override
    public Map<Integer, String> namesByIds(Collection<Integer> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> distinctIds = planIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return planRepository.findByPlanIdIn(distinctIds).stream()
                .collect(Collectors.toMap(
                        PlanEntity::getPlanId,
                        PlanEntity::getName,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    @Override
    public List<PlanRevenueRow> revenueByPlan(Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "La fecha inicial es obligatoria");
        Objects.requireNonNull(toExclusive, "La fecha final es obligatoria");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("El rango de fechas debe cumplir fromInclusive < toExclusive");
        }

        return jdbcTemplate.query(
                """
                SELECT pl.planid,
                       pl.name,
                       COUNT(pay.paymentid) AS payment_count,
                       COALESCE(SUM(pay.amount), 0) AS total_revenue
                FROM cineconecta.plans pl
                LEFT JOIN cineconecta.subscriptions sub ON sub.planid = pl.planid
                LEFT JOIN cineconecta.payments pay
                       ON pay.subscriptionid = sub.subscriptionid
                      AND pay.paidat >= ?
                      AND pay.paidat < ?
                GROUP BY pl.planid, pl.name
                ORDER BY pl.planid
                """,
                (rs, rowNum) -> new PlanRevenueRow(
                        rs.getInt("planid"),
                        rs.getString("name"),
                        rs.getLong("payment_count"),
                        rs.getBigDecimal("total_revenue") != null
                                ? rs.getBigDecimal("total_revenue")
                                : BigDecimal.ZERO
                ),
                Timestamp.from(fromInclusive),
                Timestamp.from(toExclusive)
        );
    }

    public List<SubscriptionHistoryItem> historyForAccount(int accountId) {
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByAccountIdOrderByStartsAtDesc(accountId);
        if (subscriptions.isEmpty()) {
            return List.of();
        }

        List<Integer> planIds = subscriptions.stream().map(SubscriptionEntity::getPlanId).distinct().toList();
        Map<Integer, PlanEntity> plansById = planRepository.findByPlanIdIn(planIds).stream()
                .collect(Collectors.toMap(PlanEntity::getPlanId, Function.identity()));

        List<Integer> subIds = subscriptions.stream().map(SubscriptionEntity::getSubscriptionId).toList();
        Map<Integer, PaymentEntity> paymentsBySubId = paymentRepository.findBySubscriptionIdIn(subIds).stream()
                .collect(Collectors.toMap(PaymentEntity::getSubscriptionId, Function.identity()));

        Instant now = clock.instant();
        return subscriptions.stream()
                .map(sub -> {
                    PlanEntity plan = plansById.get(sub.getPlanId());
                    PaymentEntity pay = paymentsBySubId.get(sub.getSubscriptionId());
                    SubscriptionView view = new SubscriptionView(
                            sub.getSubscriptionId(),
                            sub.getAccountId(),
                            sub.getPlanId(),
                            plan != null ? plan.getName() : "Plan #" + sub.getPlanId(),
                            plan != null ? plan.getMaxProfiles() : 0,
                            sub.getStartsAt(),
                            sub.getEndsAt()
                    );
                    boolean active = !now.isBefore(sub.getStartsAt()) && now.isBefore(sub.getEndsAt());
                    return new SubscriptionHistoryItem(
                            view,
                            active,
                            pay != null ? pay.getAmount() : null,
                            pay != null ? pay.getPaidAt() : null,
                            pay != null ? pay.getReference() : null
                    );
                })
                .toList();
    }

    public Optional<PlanSummaryView> findActivePlanById(int planId) {
        return planRepository.findById(planId)
                .filter(PlanEntity::isActiveFlag)
                .map(BillingQueryService::toPlanSummaryView);
    }

    private static SubscriptionView toSubscriptionView(SubscriptionEntity sub, PlanEntity plan) {
        return new SubscriptionView(
                sub.getSubscriptionId(),
                sub.getAccountId(),
                plan.getPlanId(),
                plan.getName(),
                plan.getMaxProfiles(),
                sub.getStartsAt(),
                sub.getEndsAt()
        );
    }

    private static PlanSummaryView toPlanSummaryView(PlanEntity plan) {
        return new PlanSummaryView(
                plan.getPlanId(),
                plan.getName(),
                plan.getPrice(),
                plan.getMaxProfiles(),
                plan.isActiveFlag()
        );
    }
}
