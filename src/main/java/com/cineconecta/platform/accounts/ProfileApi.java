package com.cineconecta.platform.accounts;

import java.util.Optional;

/**
 * API pública del módulo accounts para consulta de perfiles.
 */
public interface ProfileApi {

    Optional<ProfileView> find(int profileId);

    int countActiveProfiles(int accountId);
}
