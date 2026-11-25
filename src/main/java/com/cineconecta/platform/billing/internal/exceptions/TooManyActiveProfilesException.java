package com.cineconecta.platform.billing.internal.exceptions;

import java.io.Serial;

public class TooManyActiveProfilesException extends BillingException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int activeProfiles;
    private final int planMaxProfiles;

    public TooManyActiveProfilesException(int activeProfiles, int planMaxProfiles, String planName) {
        super("Tienes " + activeProfiles + " perfiles activos y el plan " + planName
                + " permite máximo " + planMaxProfiles + ". Reduce tus perfiles activos antes de cambiar a un plan menor.");
        this.activeProfiles = activeProfiles;
        this.planMaxProfiles = planMaxProfiles;
    }

    public int getActiveProfiles() {
        return activeProfiles;
    }

    public int getPlanMaxProfiles() {
        return planMaxProfiles;
    }
}
