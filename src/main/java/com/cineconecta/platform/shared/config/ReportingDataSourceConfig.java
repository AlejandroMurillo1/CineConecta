package com.cineconecta.platform.shared.config;

import com.cineconecta.platform.shared.persistence.ReportingJdbc;
import com.cineconecta.platform.shared.persistence.ReportingMongo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conexiones de solo lectura de los informes: {@code cine_report} en PostgreSQL y {@code cineReport}
 * en MongoDB. Por defecto usan el mismo servidor que la conexión principal de cada base; el usuario
 * y la contraseña se pueden cambiar con {@code REPORT_POSTGRES_USER}/{@code REPORT_POSTGRES_PASSWORD}
 * y {@code REPORT_MONGODB_USER}/{@code REPORT_MONGODB_PASSWORD}.
 */
@Configuration
public class ReportingDataSourceConfig {

    @Bean
    public ReportingJdbc reportingJdbc(
            @Value("${cineconecta.reporting.datasource.url:${spring.datasource.url}}") String url,
            @Value("${cineconecta.reporting.datasource.username:cine_report}") String username,
            @Value("${cineconecta.reporting.datasource.password:cine_report_dev}") String password) {
        return new ReportingJdbc(url, username, password);
    }

    @Bean
    public ReportingMongo reportingMongo(
            @Value("${cineconecta.reporting.mongodb.uri:${spring.mongodb.uri}}") String uri,
            @Value("${cineconecta.reporting.mongodb.database:${spring.mongodb.database:cineconecta}}") String database,
            @Value("${cineconecta.reporting.mongodb.username:cineReport}") String username,
            @Value("${cineconecta.reporting.mongodb.password:cine_report_dev}") String password,
            @Value("${cineconecta.mongodb.timeout-ms:5000}") long timeoutMs) {
        return ReportingMongo.connect(uri, database, username, password, timeoutMs);
    }
}
