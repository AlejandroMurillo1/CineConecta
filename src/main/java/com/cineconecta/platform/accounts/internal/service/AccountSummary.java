package com.cineconecta.platform.accounts.internal.service;

import com.cineconecta.platform.accounts.internal.persistence.AccountEntity;

/** Datos de la cuenta que se pueden exponer a la capa web (nunca el hash). */
public record AccountSummary(int accountId, String fullName, String email) {

    static AccountSummary of(AccountEntity entity) {
        return new AccountSummary(entity.getAccountId(), entity.getFullName(), entity.getEmail());
    }
}
