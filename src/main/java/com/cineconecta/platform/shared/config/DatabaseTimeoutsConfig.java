package com.cineconecta.platform.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * RNF-07: detectar pronto que MongoDB no está disponible. Por defecto el driver espera 30 s a
 * encontrar un servidor antes de fallar, y mientras tanto la página queda cargando. Con 5 s el
 * usuario ve enseguida la página 503.
 *
 * <p>PostgreSQL se configura igual con {@code spring.datasource.hikari.connection-timeout}
 * (conexión principal) y en {@code ReportingJdbc} (conexión de informes).</p>
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseTimeoutsConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoFailFastTimeouts(
            @Value("${cineconecta.mongodb.timeout-ms:5000}") long timeoutMs) {
        return settings -> settings
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(timeoutMs, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout((int) timeoutMs, TimeUnit.MILLISECONDS));
    }
}
