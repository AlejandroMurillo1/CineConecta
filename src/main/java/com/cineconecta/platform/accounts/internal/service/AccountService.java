package com.cineconecta.platform.accounts.internal.service;

import com.cineconecta.platform.accounts.internal.persistence.AccountEntity;
import com.cineconecta.platform.accounts.internal.persistence.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Registro y verificación de credenciales de clientes (RF-01, RF-02).
 */
public class AccountService {

    /** BCrypt solo usa los primeros 72 bytes; más largo se rechaza en lugar de truncarse en silencio. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** Hash válido de una contraseña cualquiera: iguala el tiempo de respuesta cuando el correo no existe. */
    private final String dummyHash;

    public AccountService(AccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.clock = Objects.requireNonNull(clock);
        this.dummyHash = passwordEncoder.encode("cineconecta-dummy-password");
    }

    @Transactional
    public AccountSummary register(String fullName, String email, String rawPassword) {
        String name = fullName.strip();
        String normalizedEmail = email.strip();
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("La contraseña no puede superar " + MAX_PASSWORD_BYTES + " bytes");
        }
        if (accounts.existsByEmailCi(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
        AccountEntity account = new AccountEntity(
                name, normalizedEmail, passwordEncoder.encode(rawPassword), clock.instant());
        try {
            // saveAndFlush: si dos registros con el mismo correo llegan a la vez, el índice único
            // ux_accounts_email_ci rechaza el segundo aquí y no al confirmar.
            AccountEntity saved = accounts.saveAndFlush(account);
            return AccountSummary.of(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
    }

    /**
     * Devuelve la cuenta si el correo y la contraseña coinciden. No distingue entre "correo
     * inexistente" y "contraseña incorrecta" para no revelar qué correos están registrados.
     */
    @Transactional(readOnly = true)
    public Optional<AccountSummary> authenticate(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return Optional.empty();
        }
        Optional<AccountEntity> account = accounts.findByEmailCi(email.strip());
        if (account.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            return Optional.empty();
        }
        return passwordEncoder.matches(rawPassword, account.get().getPasswordHash())
                ? account.map(AccountSummary::of)
                : Optional.empty();
    }
}
