package com.cineconecta.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@Testcontainers
class PlatformContextIT {

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

    @Test
    void contextLoadsWithValidatedSchema() {
    }
}
