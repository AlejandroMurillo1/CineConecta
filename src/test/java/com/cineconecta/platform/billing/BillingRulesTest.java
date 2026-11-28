package com.cineconecta.platform.billing;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.billing.internal.exceptions.ActiveSubscriptionAlreadyExistsException;
import com.cineconecta.platform.billing.internal.exceptions.ConcurrentSubscriptionException;
import com.cineconecta.platform.billing.internal.exceptions.DuplicatePlanNameException;
import com.cineconecta.platform.billing.internal.exceptions.PlanNotFoundException;
import com.cineconecta.platform.billing.internal.exceptions.PaymentRejectedException;
import com.cineconecta.platform.billing.internal.exceptions.TooManyActiveProfilesException;
import com.cineconecta.platform.billing.internal.persistence.PaymentRepository;
import com.cineconecta.platform.billing.internal.persistence.PlanEntity;
import com.cineconecta.platform.billing.internal.persistence.PlanRepository;
import com.cineconecta.platform.billing.internal.persistence.SubscriptionEntity;
import com.cineconecta.platform.billing.internal.persistence.SubscriptionRepository;
import com.cineconecta.platform.billing.internal.service.BillingQueryService;
import com.cineconecta.platform.billing.internal.service.CheckoutService;
import com.cineconecta.platform.billing.internal.service.PaymentSimulationMode;
import com.cineconecta.platform.billing.internal.service.ProfileEntitlementsAdapter;
import com.cineconecta.platform.billing.internal.service.SimulatedPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingRulesTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-25T15:00:00Z");

    @Mock
    private PlanRepository planRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProfileApi profileApi;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private Clock fixedClock;
    private CheckoutService checkoutService;
    private BillingQueryService queryService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        checkoutService = new CheckoutService(
                planRepository,
                subscriptionRepository,
                paymentRepository,
                profileApi,
                new SimulatedPaymentGateway(),
                jdbcTemplate,
                fixedClock
        );
        queryService = new BillingQueryService(
                planRepository,
                subscriptionRepository,
                paymentRepository,
                jdbcTemplate,
                fixedClock
        );
    }

    @Test
    void rejectsCheckoutWhenActiveProfilesExceedTargetPlanLimit() {
        PlanEntity basic = planWithId(1, "Básico", new BigDecimal("16900.00"), 1, "Y");
        when(planRepository.findById(1)).thenReturn(Optional.of(basic));
        when(subscriptionRepository.findActiveAt(10, FIXED_NOW)).thenReturn(Optional.empty());
        when(profileApi.countActiveProfiles(10)).thenReturn(3);

        TooManyActiveProfilesException ex = assertThrows(
                TooManyActiveProfilesException.class,
                () -> checkoutService.purchaseSubscription(10, 1, PaymentSimulationMode.APPROVED)
        );

        assertEquals(3, ex.getActiveProfiles());
        assertEquals(1, ex.getPlanMaxProfiles());
        verify(subscriptionRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCheckoutWhenSimulatedPaymentIsRejectedWithoutSavingAnything() {
        PlanEntity standard = planWithId(2, "Estándar", new BigDecimal("26900.00"), 3, "Y");
        when(planRepository.findById(2)).thenReturn(Optional.of(standard));
        when(subscriptionRepository.findActiveAt(10, FIXED_NOW)).thenReturn(Optional.empty());
        when(profileApi.countActiveProfiles(10)).thenReturn(2);

        assertThrows(
                PaymentRejectedException.class,
                () -> checkoutService.purchaseSubscription(10, 2, PaymentSimulationMode.REJECTED)
        );

        verify(subscriptionRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(jdbcTemplate).execute(ArgumentMatchers.<ConnectionCallback<Void>>any());
    }

    @Test
    void rejectsCheckoutWhenAccountAlreadyHasActiveSubscription() {
        PlanEntity standard = planWithId(2, "Estándar", new BigDecimal("26900.00"), 3, "Y");
        SubscriptionEntity existing = new SubscriptionEntity(
                10, 2, FIXED_NOW.minus(Duration.ofDays(5)), FIXED_NOW.plus(Duration.ofDays(25)));
        when(planRepository.findById(2)).thenReturn(Optional.of(standard));
        when(subscriptionRepository.findActiveAt(10, FIXED_NOW)).thenReturn(Optional.of(existing));

        assertThrows(
                ActiveSubscriptionAlreadyExistsException.class,
                () -> checkoutService.purchaseSubscription(10, 2, PaymentSimulationMode.APPROVED)
        );
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void profileEntitlementsReturnsCurrentPlanLimitOrZeroWhenExpired() {
        SubscriptionApi subscriptionApi = mock(SubscriptionApi.class);
        ProfileEntitlementsAdapter adapter = new ProfileEntitlementsAdapter(subscriptionApi, fixedClock);

        when(subscriptionApi.current(10, FIXED_NOW)).thenReturn(Optional.of(
                new SubscriptionView(99, 10, 3, "Premium", 5,
                        FIXED_NOW, FIXED_NOW.plus(Duration.ofHours(720)))
        ));
        assertEquals(5, adapter.maxProfilesFor(10));

        when(subscriptionApi.current(11, FIXED_NOW)).thenReturn(Optional.empty());
        assertEquals(0, adapter.maxProfilesFor(11));
    }

    @Test
    void subscriptionDurationConstantIsExact720Hours() {
        assertEquals(Duration.ofHours(720), CheckoutService.SUBSCRIPTION_DURATION);
    }

    @Test
    void queryServiceCurrentRequiresExactHalfOpenMatchFromRepo() {
        PlanEntity plan = planWithId(2, "Estándar", new BigDecimal("26900.00"), 3, "Y");
        Instant end = FIXED_NOW.plus(Duration.ofHours(720));
        SubscriptionEntity sub = subscriptionWithId(77, 10, 2, FIXED_NOW, end);

        when(subscriptionRepository.findActiveAt(10, FIXED_NOW)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findActiveAt(10, end)).thenReturn(Optional.empty());
        when(planRepository.findById(2)).thenReturn(Optional.of(plan));

        assertTrue(queryService.current(10, FIXED_NOW).isPresent());
        assertTrue(queryService.current(10, end).isEmpty());
    }

    private static SubscriptionEntity subscriptionWithId(
            int id, int accountId, int planId, Instant startsAt, Instant endsAt) {
        SubscriptionEntity entity = new SubscriptionEntity(accountId, planId, startsAt, endsAt);
        try {
            var field = SubscriptionEntity.class.getDeclaredField("subscriptionId");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }

    @Test
    void overlapResolvedByExclusionOrDeadlockIsReportedAsConcurrentSubscription() {
        PlanEntity standard = planWithId(2, "Estándar", new BigDecimal("26900.00"), 3, "Y");
        when(planRepository.findById(2)).thenReturn(Optional.of(standard));
        when(subscriptionRepository.findActiveAt(10, FIXED_NOW)).thenReturn(Optional.empty());
        when(profileApi.countActiveProfiles(10)).thenReturn(1);
        when(subscriptionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("exclusión",
                        new SQLException("conflicting key value violates exclusion constraint", "23P01")))
                .thenThrow(new CannotAcquireLockException("deadlock",
                        new SQLException("deadlock detected", "40P01")));

        assertThrows(ConcurrentSubscriptionException.class,
                () -> checkoutService.purchaseSubscription(10, 2, PaymentSimulationMode.APPROVED));
        assertThrows(ConcurrentSubscriptionException.class,
                () -> checkoutService.purchaseSubscription(10, 2, PaymentSimulationMode.APPROVED));
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void otherDatabaseErrorsOnSubscriptionInsertAreNotMasked() {
        PlanEntity standard = planWithId(2, "Estándar", new BigDecimal("26900.00"), 3, "Y");
        when(planRepository.findById(2)).thenReturn(Optional.of(standard));
        when(subscriptionRepository.findActiveAt(10, FIXED_NOW)).thenReturn(Optional.empty());
        when(profileApi.countActiveProfiles(10)).thenReturn(1);
        when(subscriptionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("check",
                        new SQLException("violates check constraint", "23514")));

        assertThrows(DataIntegrityViolationException.class,
                () -> checkoutService.purchaseSubscription(10, 2, PaymentSimulationMode.APPROVED));
    }

    // RF-12: gestión de planes por el administrador

    @Test
    void createPlanTrimsNameAndCreatesItActive() {
        when(planRepository.existsByNameIgnoreCase("Familiar")).thenReturn(false);
        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> savedWithId(inv.getArgument(0)));

        AdminPlanView created = queryService.createPlan("  Familiar ", new BigDecimal("45900.00"), 6);

        assertEquals("Familiar", created.name());
        assertTrue(created.active());
        assertEquals(0L, created.activeSubscriptions());
    }

    @Test
    void createPlanRejectsDuplicateNameIgnoringCase() {
        when(planRepository.existsByNameIgnoreCase("básico")).thenReturn(true);

        assertThrows(DuplicatePlanNameException.class,
                () -> queryService.createPlan("básico", new BigDecimal("10000"), 1));
        verify(planRepository, never()).save(any());
    }

    @Test
    void createPlanValidatesPriceProfilesAndNameLength() {
        assertThrows(IllegalArgumentException.class,
                () -> queryService.createPlan("Gratis", BigDecimal.ZERO, 1));
        assertThrows(IllegalArgumentException.class,
                () -> queryService.createPlan("Grande", new BigDecimal("10000"), 11));
        assertThrows(IllegalArgumentException.class,
                () -> queryService.createPlan("x".repeat(61), new BigDecimal("10000"), 1));
        verify(planRepository, never()).save(any());
    }

    @Test
    void createPlanAcceptsNamesUpToTheSchemaLength() {
        String sixty = "x".repeat(60);
        when(planRepository.existsByNameIgnoreCase(sixty)).thenReturn(false);
        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> savedWithId(inv.getArgument(0)));

        assertEquals(sixty, queryService.createPlan(sixty, new BigDecimal("10000"), 1).name());
    }

    @Test
    void renamePlanRejectsNameUsedByAnotherPlan() {
        PlanEntity basic = planWithId(1, "Básico", new BigDecimal("16900.00"), 1, "Y");
        when(planRepository.findById(1)).thenReturn(Optional.of(basic));
        when(planRepository.existsByNameIgnoreCaseAndPlanIdNot("Premium", 1)).thenReturn(true);

        assertThrows(DuplicatePlanNameException.class, () -> queryService.renamePlan(1, "Premium"));
        assertEquals("Básico", basic.getName());
    }

    @Test
    void renameAndToggleChangeTheEntity() {
        PlanEntity basic = planWithId(1, "Básico", new BigDecimal("16900.00"), 1, "Y");
        when(planRepository.findById(1)).thenReturn(Optional.of(basic));
        when(planRepository.existsByNameIgnoreCaseAndPlanIdNot("Esencial", 1)).thenReturn(false);

        queryService.renamePlan(1, " Esencial ");
        queryService.setPlanActive(1, false);

        assertEquals("Esencial", basic.getName());
        assertEquals("N", basic.getIsActive());
    }

    @Test
    void unknownPlanCannotBeToggled() {
        when(planRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(PlanNotFoundException.class, () -> queryService.setPlanActive(99, true));
    }

    /** Simula la identidad que asigna PostgreSQL al guardar. */
    private static PlanEntity savedWithId(PlanEntity plan) {
        return planWithId(4, plan.getName(), plan.getPrice(), plan.getMaxProfiles(), plan.getIsActive());
    }

    private static PlanEntity planWithId(int id, String name, BigDecimal price, int maxProfiles, String active) {
        PlanEntity entity = new PlanEntity(name, price, maxProfiles, active);
        try {
            var field = PlanEntity.class.getDeclaredField("planId");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }
}
