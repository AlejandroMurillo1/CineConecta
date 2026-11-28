package com.cineconecta.platform.reporting;

import com.cineconecta.platform.shared.persistence.ReportingMongo;
import com.cineconecta.platform.viewing.internal.persistence.ConsumptionQuery;
import com.mongodb.MongoException;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Los informes leen MongoDB con cineReport (rol read), que no puede escribir. Usa MongoDB con
 * autenticación y los scripts reales de db/mongo/init (colecciones y usuarios), como en docker compose.
 */
@Testcontainers
class ReportingMongoIT {

    @Container
    static final GenericContainer<?> mongo = new GenericContainer<>("mongo:8")
            .withEnv("MONGO_INITDB_ROOT_USERNAME", "root")
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", "root")
            .withEnv("MONGO_INITDB_DATABASE", "cineconecta")
            .withCopyFileToContainer(MountableFile.forHostPath("db/mongo/init/01_collections.js"),
                    "/docker-entrypoint-initdb.d/01_collections.js")
            .withCopyFileToContainer(MountableFile.forHostPath("db/mongo/init/03_users.js"),
                    "/docker-entrypoint-initdb.d/03_users.js")
            .withExposedPorts(27017)
            // El servidor temporal de la inicialización también lo escribe: la segunda vez es el definitivo.
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 2)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static ReportingMongo connectAsCineReport() {
        String uri = "mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017) + "/cineconecta";
        return ReportingMongo.connect(uri, "cineconecta", "cineReport", "cine_report_dev", 5000);
    }

    @Test
    void cineReportCanReadTheSessions() {
        try (ReportingMongo reporting = connectAsCineReport()) {
            var rows = new ConsumptionQuery(reporting).byPlanAndType(
                    Instant.parse("2026-11-01T05:00:00Z"), Instant.parse("2026-12-01T05:00:00Z"));
            assertEquals(0, rows.size());
        }
    }

    @Test
    void cineReportCannotWrite() {
        try (ReportingMongo reporting = connectAsCineReport()) {
            var sessions = reporting.template().getCollection("playback_sessions");
            assertThrows(MongoException.class, () -> sessions.insertOne(new Document("profileId", 1)));
            assertThrows(MongoException.class, () -> sessions.deleteMany(new Document()));
        }
    }
}
