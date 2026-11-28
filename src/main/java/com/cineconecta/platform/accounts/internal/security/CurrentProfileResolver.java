package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.service.ProfileService;
import com.cineconecta.platform.shared.CurrentAccountProvider;
import com.cineconecta.platform.shared.security.CineClaims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Perfil en uso de la petición actual.
 *
 * <p>El {@code profileId} viene del JWT, pero el JWT no es la fuente de verdad: cada vez se vuelve a
 * leer el perfil en PostgreSQL y solo se acepta si pertenece a la cuenta del token y sigue activo.
 * Así, desactivar un perfil surte efecto de inmediato aunque el token aún no haya expirado.</p>
 */
@Component
public class CurrentProfileResolver {

    private final CurrentAccountProvider currentAccount;
    private final ProfileService profiles;

    public CurrentProfileResolver(CurrentAccountProvider currentAccount, ProfileService profiles) {
        this.currentAccount = currentAccount;
        this.profiles = profiles;
    }

    public Optional<ProfileView> current() {
        OptionalInt accountId = currentAccount.currentAccountId();
        OptionalInt profileId = profileIdClaim();
        if (accountId.isEmpty() || profileId.isEmpty()) {
            return Optional.empty();
        }
        return profiles.selectable(accountId.getAsInt(), profileId.getAsInt());
    }

    /** {@code profileId} que trae el token, sin verificar (solo para saber si hay que reemitirlo). */
    public OptionalInt profileIdClaim() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token
                && token.getToken().getClaim(CineClaims.PROFILE_ID) instanceof Number n) {
            return OptionalInt.of(n.intValue());
        }
        return OptionalInt.empty();
    }
}
