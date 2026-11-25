package com.cineconecta.platform.billing;

import com.cineconecta.platform.accounts.ProfileEntitlements;
import com.cineconecta.platform.billing.internal.exceptions.ActiveSubscriptionAlreadyExistsException;
import com.cineconecta.platform.billing.internal.exceptions.ConcurrentSubscriptionException;
import com.cineconecta.platform.billing.internal.exceptions.PaymentRejectedException;
import com.cineconecta.platform.billing.internal.exceptions.TooManyActiveProfilesException;
import com.cineconecta.platform.billing.internal.service.CheckoutService;
import com.cineconecta.platform.billing.internal.service.PaymentSimulationMode;
import com.cineconecta.platform.billing.internal.service.PurchaseResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class BillingTransactionIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("cineconecta")
            .withUsername("cine")
            .withPassword("cine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("db/postgres"),
                    "/docker-entrypoint-initdb.d/"
            );

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    private static JdbcTemplate adminJdbc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgres.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        registry.add("spring.datasource.url", () -> jdbcUrl + separator + "currentSchema=cineconecta");
        registry.add("spring.datasource.username", () -> "cine_app");
        registry.add("spring.datasource.password", () -> "cine_app_dev");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.mongodb.database", () -> "cineconecta");
    }

    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private SubscriptionApi subscriptionApi;
    @Autowired
    private PlanApi planApi;
    @Autowired
    private RevenueApi revenueApi;
    @Autowired
    private ProfileEntitlements profileEntitlements;

    @BeforeAll
    static void initAdminJdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        adminJdbc = new JdbcTemplate(ds);
    }

    @BeforeEach
    void cleanTransactionalTables() {
        adminJdbc.execute("TRUNCATE TABLE cineconecta.payments, cineconecta.subscriptions, cineconecta.profiles, cineconecta.accounts RESTART IDENTITY CASCADE");
    }

    @Test
    void purchasesSubscriptionAndPaymentInSingleTransactionWith720hValidity() {
        int accountId = createAccount("titular1@cineconecta.local");
        createProfile(accountId, "Principal", 18, true);

        PurchaseResult result = checkoutService.purchaseSubscription(accountId, 2, PaymentSimulationMode.APPROVED);

        assertEquals(accountId, result.subscription().accountId());
        assertEquals("Estándar", result.subscription().planName());
        assertEquals(3, result.subscription().maxProfiles());
        assertEquals(Duration.ofHours(720),
                Duration.between(result.subscription().startsAt(), result.subscription().endsAt()));
        assertEquals(0, new BigDecimal("26900.00").compareTo(result.amount()));
        assertTrue(result.reference().startsWith("SIM-2-"));

        assertEquals(1, countRows("cineconecta.subscriptions"));
        assertEquals(1, countRows("cineconecta.payments"));
        assertEquals(3, profileEntitlements.maxProfilesFor(accountId));

        Instant start = result.subscription().startsAt();
        Instant end = result.subscription().endsAt();
        assertTrue(subscriptionApi.current(accountId, start).isPresent());
        assertTrue(subscriptionApi.current(accountId, end.minusMillis(1)).isPresent());
        assertTrue(subscriptionApi.current(accountId, end).isEmpty());
        assertEquals("Estándar", subscriptionApi.planAt(accountId, start).orElseThrow().name());
    }

    @Test
    void rejectedPaymentLeavesNoSubscriptionOrPaymentRows() {
        int accountId = createAccount("rechazo@cineconecta.local");
        createProfile(accountId, "Principal", 18, true);

        assertThrows(PaymentRejectedException.class,
                () -> checkoutService.purchaseSubscription(accountId, 1, PaymentSimulationMode.REJECTED));

        assertEquals(0, countRows("cineconecta.subscriptions"));
        assertEquals(0, countRows("cineconecta.payments"));
        assertEquals(0, profileEntitlements.maxProfilesFor(accountId));
    }

    @Test
    void midTransactionFailureRollsBackSubscriptionCompletely() {
        int accountId = createAccount("fallo@cineconecta.local");
        createProfile(accountId, "Principal", 18, true);

        assertThrows(IllegalStateException.class, () ->
                checkoutService.purchaseForVerification(
                        accountId,
                        2,
                        PaymentSimulationMode.APPROVED,
                        true,
                        () -> {
                            throw new IllegalStateException("Fallo simulado tras insertar suscripción");
                        }
                ));

        assertEquals(0, countRows("cineconecta.subscriptions"));
        assertEquals(0, countRows("cineconecta.payments"));
    }

    @Test
    void deferredDatabaseTriggerRejectsSubscriptionWithoutPaymentOnCommit() throws SQLException {
        int accountId = createAccount("trigger@cineconecta.local");

        String jdbcUrl = postgres.getJdbcUrl();
        String sep = jdbcUrl.contains("?") ? "&" : "?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl + sep + "currentSchema=cineconecta", "cine_app", "cine_app_dev")) {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement(
                    "INSERT INTO cineconecta.subscriptions (accountid, planid, startsat, endsat) " +
                    "VALUES (?, 1, now(), now() + INTERVAL '720 hours')")) {
                ps.setInt(1, accountId);
                ps.executeUpdate();
            }
            SQLException ex = assertThrows(SQLException.class, conn::commit);
            assertEquals("23514", ex.getSQLState());
            conn.rollback();
        }

        assertEquals(0, countRows("cineconecta.subscriptions"));
    }

    @Test
    void concurrentPurchasesWithAdvisoryLockAllowOnlyOneSubscription() throws Exception {
        int accountId = createAccount("concurrencia1@cineconecta.local");
        createProfile(accountId, "Principal", 18, true);

        runConcurrentCheckoutPair(accountId, true);

        assertEquals(1, countRows("cineconecta.subscriptions"));
        assertEquals(1, countRows("cineconecta.payments"));
    }

    @Test
    void concurrentPurchasesWithoutAdvisoryLockAreStoppedByGistExcludeConstraint() throws Exception {
        int accountId = createAccount("concurrencia2@cineconecta.local");
        createProfile(accountId, "Principal", 18, true);

        OutcomePair outcomes = runConcurrentCheckoutPair(accountId, false);

        assertEquals(1, outcomes.successes());
        assertEquals(1, outcomes.failures());
        assertEquals(1, countRows("cineconecta.subscriptions"));
        assertEquals(1, countRows("cineconecta.payments"));
    }

    @Test
    void enforcesProfileReductionBeforeDowngradingToSmallerPlan() {
        int accountId = createAccount("downgrade@cineconecta.local");
        int p1 = createProfile(accountId, "Uno", 18, true);
        int p2 = createProfile(accountId, "Dos", 13, true);
        int p3 = createProfile(accountId, "Tres", 7, true);

        assertThrows(TooManyActiveProfilesException.class,
                () -> checkoutService.purchaseSubscription(accountId, 1, PaymentSimulationMode.APPROVED));
        assertEquals(0, countRows("cineconecta.subscriptions"));

        adminJdbc.update("UPDATE cineconecta.profiles SET isactive = 'N' WHERE profileid IN (?, ?)", p2, p3);

        PurchaseResult result = checkoutService.purchaseSubscription(accountId, 1, PaymentSimulationMode.APPROVED);
        assertEquals(1, result.subscription().maxProfiles());
        assertNotNull(p1);
    }

    @Test
    void exposesPlanCatalogAndRevenueAggregationByPlan() {
        int a1 = createAccount("rev1@cineconecta.local");
        int a2 = createAccount("rev2@cineconecta.local");

        PurchaseResult r1 = checkoutService.purchaseSubscription(a1, 1, PaymentSimulationMode.APPROVED);
        PurchaseResult r2 = checkoutService.purchaseSubscription(a2, 3, PaymentSimulationMode.APPROVED);

        List<PlanSummaryView> activePlans = planApi.findActivePlans();
        assertEquals(3, activePlans.size());

        Map<Integer, String> names = planApi.namesByIds(List.of(1, 3));
        assertEquals("Básico", names.get(1));
        assertEquals("Premium", names.get(3));

        Instant from = r1.paidAt().minusSeconds(60);
        Instant to = r2.paidAt().plusSeconds(60);
        List<PlanRevenueRow> rows = revenueApi.revenueByPlan(from, to);

        PlanRevenueRow basicRow = rows.stream().filter(r -> r.planId() == 1).findFirst().orElseThrow();
        PlanRevenueRow standardRow = rows.stream().filter(r -> r.planId() == 2).findFirst().orElseThrow();
        PlanRevenueRow premiumRow = rows.stream().filter(r -> r.planId() == 3).findFirst().orElseThrow();

        assertEquals(1L, basicRow.paymentCount());
        assertEquals(0, new BigDecimal("16900.00").compareTo(basicRow.totalRevenue()));
        assertEquals(0L, standardRow.paymentCount());
        assertEquals(1L, premiumRow.paymentCount());
        assertEquals(0, new BigDecimal("38900.00").compareTo(premiumRow.totalRevenue()));
    }

    private OutcomePair runConcurrentCheckoutPair(int accountId, boolean useAdvisoryLock) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Runnable task = () -> {
                ready.countDown();
                try {
                    assertTrue(go.await(10, TimeUnit.SECONDS));
                    checkoutService.purchaseForVerification(
                            accountId, 2, PaymentSimulationMode.APPROVED, useAdvisoryLock, () -> {});
                    successes.incrementAndGet();
                } catch (ActiveSubscriptionAlreadyExistsException | ConcurrentSubscriptionException ex) {
                    failures.incrementAndGet();
                } catch (Exception ex) {
                    throw new AssertionError("Excepción inesperada en concurrencia", ex);
                }
            };

            Future<?> f1 = pool.submit(task);
            Future<?> f2 = pool.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            f1.get(15, TimeUnit.SECONDS);
            f2.get(15, TimeUnit.SECONDS);
        }

        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        return new OutcomePair(successes.get(), failures.get());
    }

    private int createAccount(String email) {
        Integer id = adminJdbc.queryForObject(
                """
                INSERT INTO cineconecta.accounts (fullname, email, passwordhash)
                VALUES ('Usuario Prueba', ?, '$2a$10$7EqJtq98hPqEX7fNZaFWoO5r5Q6fT7wI6Q2u5q7L2bM2Q8S6l9x2K')
                RETURNING accountid
                """,
                Integer.class,
                email
        );
        assertNotNull(id);
        return id;
    }

    private int createProfile(int accountId, String name, int maxAge, boolean active) {
        Integer id = adminJdbc.queryForObject(
                """
                INSERT INTO cineconecta.profiles (accountid, name, maxagerating, isactive)
                VALUES (?, ?, ?, ?)
                RETURNING profileid
                """,
                Integer.class,
                accountId, name, maxAge, active ? "Y" : "N"
        );
        assertNotNull(id);
        return id;
    }

    private int countRows(String table) {
        Integer count = adminJdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count != null ? count : 0;
    }

    private record OutcomePair(int successes, int failures) {
    }
}
