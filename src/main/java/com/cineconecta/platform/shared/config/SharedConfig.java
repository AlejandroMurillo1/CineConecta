package com.cineconecta.platform.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuración compartida base: reloj UTC único para toda la aplicación (las pruebas lo
 * reemplazan por uno fijo).
 *
 * <p>La seguridad ya no tiene respaldo aquí: la cadena de clientes y el {@code CurrentAccountProvider}
 * los aporta el módulo accounts, y la de administradores el módulo backoffice.</p>
 */
@Configuration
public class SharedConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
