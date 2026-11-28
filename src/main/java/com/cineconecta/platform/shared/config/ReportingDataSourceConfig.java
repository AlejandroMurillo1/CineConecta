package com.cineconecta.platform.shared.config;

import com.cineconecta.platform.shared.persistence.ReportingJdbc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conexión de los informes con el usuario {@code cine_report} (solo lectura).
 * Por defecto usa la misma URL que la conexión principal; el usuario y la contraseña se pueden
 * cambiar con {@code REPORT_POSTGRES_USER} y {@code REPORT_POSTGRES_PASSWORD}.
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
}
