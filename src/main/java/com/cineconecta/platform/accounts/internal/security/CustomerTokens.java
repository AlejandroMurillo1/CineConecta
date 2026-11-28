package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.shared.security.AuthCookies;
import com.cineconecta.platform.shared.security.CineClaims;
import com.cineconecta.platform.shared.security.JwtTokenService;
import com.cineconecta.platform.shared.security.TokenType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Emite (o reemite) el JWT de cliente en su cookie. Se reemite al iniciar sesión, al elegir perfil
 * (agrega {@code profileId}) y al desactivar el perfil en uso (lo quita).
 */
@Component
public class CustomerTokens {

    private final JwtTokenService tokens;
    private final AuthCookies cookies;

    public CustomerTokens(JwtTokenService tokens, AuthCookies cookies) {
        this.tokens = tokens;
        this.cookies = cookies;
    }

    public void signIn(HttpServletResponse response, int accountId, Integer profileId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CineClaims.ACCOUNT_ID, accountId);
        if (profileId != null) {
            claims.put(CineClaims.PROFILE_ID, profileId);
        }
        cookies.write(response, tokens.issue(TokenType.CUSTOMER, String.valueOf(accountId), claims));
    }
}
