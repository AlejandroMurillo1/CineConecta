package com.cineconecta.platform.accounts.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Cuenta del titular ({@code ACCOUNTS}). Inmutable desde la aplicación: {@code cine_app} solo tiene
 * SELECT e INSERT sobre la tabla.
 */
@Entity
@Immutable
@Table(name = "accounts", schema = "cineconecta")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer accountId;

    @Column(nullable = false, updatable = false, length = 120)
    private String fullName;

    @Column(nullable = false, updatable = false, length = 254)
    private String email;

    @Column(nullable = false, updatable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountEntity() {
    }

    public AccountEntity(String fullName, String email, String passwordHash, Instant createdAt) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
