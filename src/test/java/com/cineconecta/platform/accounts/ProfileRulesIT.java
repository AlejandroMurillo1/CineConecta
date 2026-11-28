package com.cineconecta.platform.accounts;

import com.cineconecta.platform.accounts.internal.service.ProfileRuleException;
import com.cineconecta.platform.accounts.internal.service.ProfileRuleException.Reason;
import com.cineconecta.platform.accounts.internal.service.ProfileService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de perfiles contra PostgreSQL real, con billing real (ProfileEntitlements) y el usuario
 * de mínimo privilegio cine_app.
 */
@SpringBootTest
@Testcontainers
class ProfileRulesIT {

    /** Planes de 03_plans.sql. */
    private static final int BASICO = 1;     // 1 perfil
    private static final int ESTANDAR = 2;   // 3 perfiles

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("cineconecta")
            .withUsername("cine")
            .withPassword("cine")
            .withCopyFileToContainer(MountableFile.forHostPath("db/postgres"), "/docker-entrypoint-initdb.d/");

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
    private ProfileService profiles;
    @Autowired
    private ProfileApi profileApi;

    @BeforeAll
    static void initAdminJdbc() {
        adminJdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @BeforeEach
    void clean() {
        adminJdbc.execute("TRUNCATE TABLE cineconecta.payments, cineconecta.subscriptions, "
                + "cineconecta.profiles, cineconecta.accounts RESTART IDENTITY CASCADE");
    }

    private int createAccount(String email) {
        return adminJdbc.queryForObject("""
                INSERT INTO cineconecta.accounts (fullname, email, passwordhash)
                VALUES ('Titular', ?, '$2a$10$7EqJtq98hPqEX7fNZaFWoO5r5Q6fT7wI6Q2u5q7L2bM2Q8S6l9x2K')
                RETURNING accountid
                """, Integer.class, email);
    }

    /** Suscripción vigente + pago en una sola sentencia (el trigger diferido exige el pago). */
    private void subscribe(int accountId, int planId) {
        adminJdbc.update("""
                WITH s AS (
                    INSERT INTO cineconecta.subscriptions (accountid, planid, startsat, endsat)
                    VALUES (?, ?, now() - interval '1 hour', now() - interval '1 hour' + interval '720 hours')
                    RETURNING subscriptionid
                )
                INSERT INTO cineconecta.payments (subscriptionid, amount, reference)
                SELECT subscriptionid, 16900, 'IT-' || subscriptionid FROM s
                """, accountId, planId);
    }

    @Test
    void creatingAProfileRequiresACurrentSubscription() {
        int account = createAccount("sin-plan@cine.local");

        assertThatThrownBy(() -> profiles.create(account, "Ana", 12))
                .isInstanceOfSatisfying(ProfileRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.NO_ACTIVE_SUBSCRIPTION));
    }

    @Test
    void activeProfilesCannotExceedThePlanMaximum() {
        int account = createAccount("basico@cine.local");
        subscribe(account, BASICO);

        profiles.create(account, "Ana", 12);

        assertThatThrownBy(() -> profiles.create(account, "Leo", 7))
                .isInstanceOfSatisfying(ProfileRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.PROFILE_LIMIT_REACHED));
        assertThat(profileApi.countActiveProfiles(account)).isEqualTo(1);
    }

    @Test
    void nameIsUniqueWithinTheAccountIgnoringCase() {
        int account = createAccount("nombres@cine.local");
        int other = createAccount("otra@cine.local");
        subscribe(account, ESTANDAR);
        subscribe(other, ESTANDAR);

        profiles.create(account, "Ana", 12);

        assertThatThrownBy(() -> profiles.create(account, "ANA", 7))
                .isInstanceOfSatisfying(ProfileRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.DUPLICATE_NAME));
        // Otra cuenta sí puede usar el mismo nombre.
        assertThat(profiles.create(other, "Ana", 7).name()).isEqualTo("Ana");
    }

    @Test
    void deactivatingKeepsTheRowAndFreesASlot_reactivatingNeedsRoom() {
        int account = createAccount("activar@cine.local");
        subscribe(account, BASICO);
        ProfileView ana = profiles.create(account, "Ana", 12);

        profiles.deactivate(account, ana.profileId());
        ProfileView leo = profiles.create(account, "Leo", 7);

        assertThat(adminJdbc.queryForObject(
                "SELECT count(*) FROM cineconecta.profiles WHERE accountid = ?", Integer.class, account))
                .isEqualTo(2);
        assertThat(profileApi.find(ana.profileId())).get().extracting(ProfileView::active).isEqualTo(false);
        assertThatThrownBy(() -> profiles.reactivate(account, ana.profileId()))
                .isInstanceOfSatisfying(ProfileRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.PROFILE_LIMIT_REACHED));

        profiles.deactivate(account, leo.profileId());
        assertThat(profiles.reactivate(account, ana.profileId()).active()).isTrue();
    }

    @Test
    void profilesOfAnotherAccountAreInvisible() {
        int owner = createAccount("duena@cine.local");
        int intruder = createAccount("intruso@cine.local");
        subscribe(owner, ESTANDAR);
        ProfileView ana = profiles.create(owner, "Ana", 12);

        assertThat(profiles.find(intruder, ana.profileId())).isEmpty();
        assertThat(profiles.selectable(intruder, ana.profileId())).isEmpty();
        assertThatThrownBy(() -> profiles.deactivate(intruder, ana.profileId()))
                .isInstanceOfSatisfying(ProfileRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
    }

    @Test
    void concurrentCreationsCannotExceedThePlanMaximum() throws Exception {
        int account = createAccount("carrera@cine.local");
        subscribe(account, ESTANDAR);
        profiles.create(account, "Uno", 18);
        profiles.create(account, "Dos", 18);

        // Queda un cupo y llegan dos peticiones a la vez.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<ProfileView>> results = new ArrayList<>();
        for (String name : List.of("Tres-A", "Tres-B")) {
            Callable<ProfileView> task = () -> {
                start.await();
                return profiles.create(account, name, 7);
            };
            results.add(pool.submit(task));
        }
        start.countDown();

        int created = 0;
        int rejected = 0;
        for (Future<ProfileView> result : results) {
            try {
                result.get();
                created++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfSatisfying(ProfileRuleException.class,
                        rule -> assertThat(rule.reason()).isEqualTo(Reason.PROFILE_LIMIT_REACHED));
                rejected++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(profileApi.countActiveProfiles(account)).isEqualTo(3);
    }
}
