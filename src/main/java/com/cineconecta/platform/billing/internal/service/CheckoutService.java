package com.cineconecta.platform.billing.internal.service;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.billing.SubscriptionView;
import com.cineconecta.platform.billing.internal.exceptions.ActiveSubscriptionAlreadyExistsException;
import com.cineconecta.platform.billing.internal.exceptions.ConcurrentSubscriptionException;
import com.cineconecta.platform.billing.internal.exceptions.PlanNotAvailableException;
import com.cineconecta.platform.billing.internal.exceptions.TooManyActiveProfilesException;
import com.cineconecta.platform.billing.internal.persistence.PaymentEntity;
import com.cineconecta.platform.billing.internal.persistence.PaymentRepository;
import com.cineconecta.platform.billing.internal.persistence.PlanEntity;
import com.cineconecta.platform.billing.internal.persistence.PlanRepository;
import com.cineconecta.platform.billing.internal.persistence.SubscriptionEntity;
import com.cineconecta.platform.billing.internal.persistence.SubscriptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Orquestador transaccional de contratación de suscripciones con pago simulado.
 */
@Service
public class CheckoutService {

    public static final Duration SUBSCRIPTION_DURATION = Duration.ofHours(720);
    private static final String PG_EXCLUSION_VIOLATION = "23P01";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ProfileApi profileApi;
    private final SimulatedPaymentGateway paymentGateway;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CheckoutService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            PaymentRepository paymentRepository,
            ProfileApi profileApi,
            SimulatedPaymentGateway paymentGateway,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.profileApi = profileApi;
        this.paymentGateway = paymentGateway;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public PurchaseResult purchaseSubscription(int accountId, int planId, PaymentSimulationMode paymentMode) {
        return purchaseInternal(accountId, planId, paymentMode, true, () -> {});
    }

    /**
     * Variante usada en pruebas de integración para simular un fallo tras insertar la suscripción
     * (antes del pago) o desactivar el advisory lock para demostrar la restricción EXCLUDE 23P01.
     */
    @Transactional
    public PurchaseResult purchaseForVerification(
            int accountId,
            int planId,
            PaymentSimulationMode paymentMode,
            boolean acquireAdvisoryLock,
            Runnable afterSubscriptionInsertHook) {
        return purchaseInternal(accountId, planId, paymentMode, acquireAdvisoryLock,
                afterSubscriptionInsertHook != null ? afterSubscriptionInsertHook : () -> {});
    }

    private PurchaseResult purchaseInternal(
            int accountId,
            int planId,
            PaymentSimulationMode paymentMode,
            boolean acquireAdvisoryLock,
            Runnable afterSubscriptionInsertHook) {

        if (acquireAdvisoryLock) {
            acquireAccountLock(accountId);
        }

        PlanEntity plan = planRepository.findById(planId)
                .filter(PlanEntity::isActiveFlag)
                .orElseThrow(() -> new PlanNotAvailableException(planId));

        Instant now = clock.instant();
        Optional<SubscriptionEntity> existing = subscriptionRepository.findActiveAt(accountId, now);
        if (existing.isPresent()) {
            throw new ActiveSubscriptionAlreadyExistsException(existing.get().getEndsAt());
        }

        int activeProfiles = profileApi.countActiveProfiles(accountId);
        if (activeProfiles > plan.getMaxProfiles()) {
            throw new TooManyActiveProfilesException(activeProfiles, plan.getMaxProfiles(), plan.getName());
        }

        // El pago simulado se valida antes de crear registros; si se rechaza, no hay inserciones.
        String reference = paymentGateway.processPayment(plan, paymentMode);

        Instant endsAt = now.plus(SUBSCRIPTION_DURATION);
        SubscriptionEntity savedSubscription;
        try {
            savedSubscription = subscriptionRepository.saveAndFlush(
                    new SubscriptionEntity(accountId, plan.getPlanId(), now, endsAt));
        } catch (DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                throw new ConcurrentSubscriptionException(
                        "Se detectó una contratación simultánea con período superpuesto para esta cuenta.", ex);
            }
            throw ex;
        }

        afterSubscriptionInsertHook.run();

        PaymentEntity savedPayment = paymentRepository.saveAndFlush(
                new PaymentEntity(savedSubscription.getSubscriptionId(), now, plan.getPrice(), reference));

        SubscriptionView view = new SubscriptionView(
                savedSubscription.getSubscriptionId(),
                savedSubscription.getAccountId(),
                plan.getPlanId(),
                plan.getName(),
                plan.getMaxProfiles(),
                savedSubscription.getStartsAt(),
                savedSubscription.getEndsAt()
        );

        return new PurchaseResult(
                view,
                savedPayment.getPaymentId(),
                savedPayment.getAmount(),
                savedPayment.getPaidAt(),
                savedPayment.getReference()
        );
    }

    private void acquireAccountLock(int accountId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
            try (var ps = con.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                ps.setInt(1, accountId);
                ps.execute();
            }
            return null;
        });
    }

    private static boolean isExclusionViolation(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlEx && PG_EXCLUSION_VIOLATION.equals(sqlEx.getSQLState())) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
