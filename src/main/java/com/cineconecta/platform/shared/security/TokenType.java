package com.cineconecta.platform.shared.security;

/**
 * Tipos de token que emite la aplicación. Cada tipo viaja en su propia cookie y lleva su valor en el
 * claim {@code typ}, de modo que un token de cliente nunca se acepta en la cadena de administradores
 * (ni al revés).
 */
public enum TokenType {

    CUSTOMER("customer", "CINE_TOKEN"),
    ADMIN("admin", "CINE_ADMIN");

    private final String claimValue;
    private final String cookieName;

    TokenType(String claimValue, String cookieName) {
        this.claimValue = claimValue;
        this.cookieName = cookieName;
    }

    /** Valor del claim {@code typ}. */
    public String claimValue() {
        return claimValue;
    }

    /** Nombre de la cookie {@code HttpOnly} que transporta el token. */
    public String cookieName() {
        return cookieName;
    }
}
