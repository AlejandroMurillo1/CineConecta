package com.cineconecta.platform.accounts;

/**
 * Contrato declarado por accounts e implementado por billing para consultar el límite
 * de perfiles activos sin introducir ciclos entre módulos.
 */
public interface ProfileEntitlements {

    int maxProfilesFor(int accountId);
}
