package com.cineconecta.platform.accounts.internal.service;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bloqueo transaccional por cuenta en PostgreSQL ({@code pg_advisory_xact_lock(accountId)}).
 *
 * <p>Usa la MISMA clave que billing al contratar una suscripción, así que crear o reactivar
 * perfiles y contratar un plan para la misma cuenta se ejecutan de a uno. Sin esto, dos peticiones
 * simultáneas podrían leer "2 de 3 perfiles activos" y crear cada una el tercero, o contratar un
 * plan menor mientras se activa un perfil. El bloqueo se libera solo al terminar la transacción.</p>
 */
public class AccountLock {

    private final JdbcTemplate jdbc;

    public AccountLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Debe llamarse dentro de una transacción activa. */
    public void lock(int accountId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)::text", String.class, accountId);
    }
}
