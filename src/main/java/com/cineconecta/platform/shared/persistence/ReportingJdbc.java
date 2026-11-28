package com.cineconecta.platform.shared.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Conexión de solo lectura a PostgreSQL con el usuario {@code cine_report}, para los informes.
 *
 * <p>La aplicación opera con {@code cine_app}; los informes leen con {@code cine_report}, que solo
 * tiene SELECT. Así, aunque una consulta de informe tuviera un error, la base no le permite escribir.</p>
 *
 * <p>No se publica como bean {@code DataSource} ni {@code JdbcOperations} a propósito: un segundo bean
 * de esos tipos desactivaría la autoconfiguración de Spring Boot para la conexión principal.</p>
 */
public final class ReportingJdbc implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcTemplate template;

    public ReportingJdbc(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("cineconecta-report");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        // Igual que la conexión principal: no falla al arrancar si la base todavía no responde.
        config.setInitializationFailTimeout(-1);
        // RNF-07: si la base no responde, fallar en 5 s (página 503) y no en los 30 s por defecto.
        config.setConnectionTimeout(5_000);
        this.dataSource = new HikariDataSource(config);
        this.template = new JdbcTemplate(dataSource);
    }

    public JdbcTemplate template() {
        return template;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
