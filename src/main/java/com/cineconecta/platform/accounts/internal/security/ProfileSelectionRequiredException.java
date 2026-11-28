package com.cineconecta.platform.accounts.internal.security;

/** La operación necesita un perfil activo seleccionado y no lo hay (o ya no es válido). */
public class ProfileSelectionRequiredException extends RuntimeException {

    public ProfileSelectionRequiredException() {
        super("Elige un perfil para continuar");
    }
}
