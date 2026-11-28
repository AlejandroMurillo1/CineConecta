package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.shared.CurrentAccountProvider;
import com.cineconecta.platform.shared.security.CineClaims;
import com.cineconecta.platform.shared.security.TokenType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

/**
 * Cuenta autenticada tomada del JWT de cliente ya validado. Los demás módulos nunca reciben el
 * {@code accountId} por parámetro: siempre lo piden aquí.
 */
@Component
class JwtCurrentAccountProvider implements CurrentAccountProvider {

    @Override
    public OptionalInt currentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return OptionalInt.empty();
        }
        Jwt jwt = token.getToken();
        if (!TokenType.CUSTOMER.claimValue().equals(jwt.getClaimAsString(CineClaims.TYPE))) {
            return OptionalInt.empty();
        }
        Object claim = jwt.getClaim(CineClaims.ACCOUNT_ID);
        return claim instanceof Number n ? OptionalInt.of(n.intValue()) : OptionalInt.empty();
    }
}
