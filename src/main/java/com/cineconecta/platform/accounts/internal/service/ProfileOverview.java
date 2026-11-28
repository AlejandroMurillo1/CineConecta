package com.cineconecta.platform.accounts.internal.service;

import com.cineconecta.platform.accounts.ProfileView;

import java.util.List;

/**
 * Perfiles de la cuenta y cupo del plan vigente.
 *
 * @param maxProfiles máximo de perfiles activos del plan vigente; 0 si no hay suscripción vigente
 */
public record ProfileOverview(List<ProfileView> profiles, int activeCount, int maxProfiles) {

    public boolean hasSubscription() {
        return maxProfiles > 0;
    }

    public boolean canAddActive() {
        return hasSubscription() && activeCount < maxProfiles;
    }

    public List<ProfileView> active() {
        return profiles.stream().filter(ProfileView::active).toList();
    }

    public List<ProfileView> inactive() {
        return profiles.stream().filter(p -> !p.active()).toList();
    }
}
