package com.cineconecta.platform.accounts.internal.service;

import com.cineconecta.platform.accounts.internal.persistence.AccountEntity;
import com.cineconecta.platform.accounts.internal.persistence.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T14:00:00Z");

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AccountRepository repository;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(AccountRepository.class);
        service = new AccountService(repository, encoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AccountEntity saved(int id, String name, String email, String hash) {
        AccountEntity entity = new AccountEntity(name, email, hash, NOW);
        ReflectionTestUtils.setField(entity, "accountId", id);
        return entity;
    }

    @Test
    void registerStoresBcryptHashAndNeverThePlainPassword() {
        when(repository.existsByEmailCi("ana@correo.com")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            AccountEntity e = inv.getArgument(0);
            return saved(42, e.getFullName(), e.getEmail(), e.getPasswordHash());
        });

        AccountSummary account = service.register("  Ana Pérez ", " ana@correo.com ", "Secreta123");

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        AccountEntity stored = captor.getValue();
        assertThat(stored.getFullName()).isEqualTo("Ana Pérez");
        assertThat(stored.getEmail()).isEqualTo("ana@correo.com");
        assertThat(stored.getPasswordHash()).startsWith("$2").doesNotContain("Secreta123");
        assertThat(encoder.matches("Secreta123", stored.getPasswordHash())).isTrue();
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(account).isEqualTo(new AccountSummary(42, "Ana Pérez", "ana@correo.com"));
    }

    @Test
    void registerRejectsEmailAlreadyUsedIgnoringCase() {
        when(repository.existsByEmailCi("ANA@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Ana", "ANA@correo.com", "Secreta123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateCaughtByUniqueIndexIsReportedAsDuplicate() {
        when(repository.existsByEmailCi("ana@correo.com")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ux_accounts_email_ci"));

        assertThatThrownBy(() -> service.register("Ana", "ana@correo.com", "Secreta123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void registerRejectsPasswordsLongerThan72Bytes() {
        String longMultibyte = "ñ".repeat(40); // 40 caracteres, 80 bytes en UTF-8

        assertThatThrownBy(() -> service.register("Ana", "ana@correo.com", longMultibyte))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authenticateAcceptsCorrectPasswordWithEmailInAnyCase() {
        when(repository.findByEmailCi("Ana@Correo.com"))
                .thenReturn(Optional.of(saved(42, "Ana", "ana@correo.com", encoder.encode("Secreta123"))));

        assertThat(service.authenticate(" Ana@Correo.com ", "Secreta123"))
                .contains(new AccountSummary(42, "Ana", "ana@correo.com"));
    }

    @Test
    void authenticateRejectsWrongPasswordAndUnknownEmailTheSameWay() {
        when(repository.findByEmailCi("ana@correo.com"))
                .thenReturn(Optional.of(saved(42, "Ana", "ana@correo.com", encoder.encode("Secreta123"))));
        when(repository.findByEmailCi("nadie@correo.com")).thenReturn(Optional.empty());

        assertThat(service.authenticate("ana@correo.com", "otra")).isEmpty();
        assertThat(service.authenticate("nadie@correo.com", "Secreta123")).isEmpty();
        assertThat(service.authenticate(null, null)).isEmpty();
    }
}
