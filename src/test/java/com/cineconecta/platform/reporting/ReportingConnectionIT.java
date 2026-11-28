package com.cineconecta.platform.reporting;

import com.cineconecta.platform.billing.PlanRevenueRow;
import com.cineconecta.platform.billing.RevenueApi;
import com.cineconecta.platform.billing.internal.service.RevenueReportQuery;
import com.cineconecta.platform.shared.persistence.ReportingJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Los informes leen PostgreSQL con cine_report, que no puede escribir. */
@SpringBootTest
@Testcontainers
class ReportingConnectionIT {

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
    private ReportingJdbc reportingJdbc;

    @Autowired
    private RevenueApi revenueApi;

    @Test
    void reportsConnectAsCineReport() {
        String user = reportingJdbc.template().queryForObject("SELECT current_user", String.class);
        assertEquals("cine_report", user);
    }

    @Test
    void reportConnectionCannotWrite() {
        assertThrows(DataAccessException.class, () -> reportingJdbc.template().update(
                "INSERT INTO cineconecta.plans (name, price, maxProfiles) VALUES ('Prueba', 1000, 1)"));
    }

    @Test
    void reportingReceivesTheRevenueQueryThatUsesCineReport() {
        assertInstanceOf(RevenueReportQuery.class, revenueApi);

        List<PlanRevenueRow> rows = revenueApi.revenueByPlan(
                Instant.parse("2026-01-01T05:00:00Z"), Instant.parse("2026-02-01T05:00:00Z"));

        assertEquals(3, rows.size()); // los tres planes iniciales, sin pagos en ese período
    }
}
