package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.accounts.ProfileEntitlements;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.catalog.ViewerContext;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

/**
 * Implementación real del puerto {@link ViewerContext} del catálogo: la clasificación máxima del
 * perfil en uso.
 *
 * <p>Antes de responder garantiza lo que el catálogo espera (RF-20 a RF-22): perfil seleccionado,
 * activo y de la cuenta autenticada, y suscripción vigente. Si falta algo lanza una excepción que
 * {@code ProfileGuardAdvice} convierte en una redirección (a elegir perfil o a planes).</p>
 *
 * <p>La suscripción vigente se consulta con {@link ProfileEntitlements} (implementado por billing):
 * devuelve 0 si no hay suscripción vigente, y todo plan permite al menos un perfil.</p>
 */
@Component
class ProfileViewerContext implements ViewerContext {

    private final CurrentProfileResolver currentProfile;
    private final ProfileEntitlements entitlements;

    ProfileViewerContext(CurrentProfileResolver currentProfile, ProfileEntitlements entitlements) {
        this.currentProfile = currentProfile;
        this.entitlements = entitlements;
    }

    @Override
    public int maxAgeRating() {
        ProfileView profile = currentProfile.require();
        if (entitlements.maxProfilesFor(profile.accountId()) <= 0) {
            throw new SubscriptionRequiredException();
        }
        return profile.maxAgeRating();
    }

    @Override
    public OptionalInt allowedMaxAgeRating() {
        return currentProfile.current()
                .filter(profile -> entitlements.maxProfilesFor(profile.accountId()) > 0)
                .map(profile -> OptionalInt.of(profile.maxAgeRating()))
                .orElse(OptionalInt.empty());
    }
}
