package com.cineconecta.platform.accounts.internal.service;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Ya existe una cuenta con el correo " + email);
    }
}
