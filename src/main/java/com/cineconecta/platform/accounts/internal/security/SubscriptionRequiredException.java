package com.cineconecta.platform.accounts.internal.security;

/** La operación necesita una suscripción vigente y la cuenta no la tiene. */
public class SubscriptionRequiredException extends RuntimeException {

    public SubscriptionRequiredException() {
        super("Necesitas una suscripción vigente");
    }
}
