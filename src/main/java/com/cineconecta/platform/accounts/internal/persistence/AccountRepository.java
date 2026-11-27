package com.cineconecta.platform.accounts.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Integer> {

    /**
     * Busca por correo sin distinguir mayúsculas. Se usa {@code lower()} (y no la consulta derivada
     * {@code findByEmailIgnoreCase}, que genera {@code upper()}) para aprovechar el índice
     * {@code ux_accounts_email_ci ON accounts (LOWER(email))}.
     */
    @Query("SELECT a FROM AccountEntity a WHERE lower(a.email) = lower(:email)")
    Optional<AccountEntity> findByEmailCi(@Param("email") String email);

    @Query("SELECT count(a) > 0 FROM AccountEntity a WHERE lower(a.email) = lower(:email)")
    boolean existsByEmailCi(@Param("email") String email);
}
