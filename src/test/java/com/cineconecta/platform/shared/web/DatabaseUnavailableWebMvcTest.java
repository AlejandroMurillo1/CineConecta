package com.cineconecta.platform.shared.web;

import com.cineconecta.platform.shared.PermitAllSecurityTestConfig;
import com.mongodb.MongoTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.SQLException;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RNF-07: una base caída responde 503 con una página que dice qué parte del servicio falla. */
@WebMvcTest(controllers = DatabaseUnavailableWebMvcTest.FailingController.class)
@Import({PermitAllSecurityTestConfig.class, DatabaseUnavailableAdvice.class,
        DatabaseUnavailableWebMvcTest.FailingController.class})
class DatabaseUnavailableWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    /** Simula lo que lanzan JPA, JdbcTemplate y el driver de MongoDB cuando su base no responde. */
    @Controller
    static class FailingController {
        @GetMapping("/test/db-down")
        String fail(@RequestParam String db) {
            throw switch (db) {
                case "jpa" -> new CannotCreateTransactionException("Could not open JPA EntityManager",
                        new SQLException("Connection to localhost:5432 refused"));
                case "jdbc" -> new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                        new SQLException("Connection to localhost:5432 refused"));
                case "mongo-spring" -> new DataAccessResourceFailureException("Timed out",
                        new MongoTimeoutException("Timed out while waiting for a server"));
                case "mongo-driver" -> new MongoTimeoutException("Timed out while waiting for a server");
                default -> new IllegalArgumentException(db);
            };
        }
    }

    @Test
    void postgresDownShowsAccountsAreUnavailable() throws Exception {
        for (String db : new String[]{"jpa", "jdbc"}) {
            mockMvc.perform(get("/test/db-down").param("db", db))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(content().string(allOf(
                            containsString("Servicio no disponible"),
                            containsString("las cuentas, los perfiles ni las suscripciones"),
                            not(containsString("5432")),
                            not(containsString("refused")))));
        }
    }

    @Test
    void mongoDownShowsCatalogIsUnavailableWhetherTranslatedOrNot() throws Exception {
        for (String db : new String[]{"mongo-spring", "mongo-driver"}) {
            mockMvc.perform(get("/test/db-down").param("db", db))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string(allOf(
                            containsString("al catálogo ni a tu actividad"),
                            not(containsString("Timed out")))));
        }
    }

    @Test
    void identifiesTheDatabaseFromTheCauseChain() {
        org.assertj.core.api.Assertions.assertThat(DatabaseUnavailableAdvice.identify(
                        new DataAccessResourceFailureException("x", new MongoTimeoutException("y"))))
                .isEqualTo(DatabaseUnavailableAdvice.Database.MONGO);
        org.assertj.core.api.Assertions.assertThat(DatabaseUnavailableAdvice.identify(
                        new CannotGetJdbcConnectionException("x", new SQLException("y"))))
                .isEqualTo(DatabaseUnavailableAdvice.Database.POSTGRES);
        org.assertj.core.api.Assertions.assertThat(DatabaseUnavailableAdvice.identify(
                        new DataAccessResourceFailureException("sin causa")))
                .isEqualTo(DatabaseUnavailableAdvice.Database.UNKNOWN);
    }
}
