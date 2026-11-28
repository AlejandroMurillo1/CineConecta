package com.cineconecta.platform.accounts.internal.service;

/**
 * Una regla de negocio de perfiles impide la operación. El mensaje está pensado para mostrarse al
 * usuario tal cual.
 */
public class ProfileRuleException extends RuntimeException {

    public enum Reason {
        NO_ACTIVE_SUBSCRIPTION,
        PROFILE_LIMIT_REACHED,
        DUPLICATE_NAME,
        NOT_FOUND
    }

    private final Reason reason;

    public ProfileRuleException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
