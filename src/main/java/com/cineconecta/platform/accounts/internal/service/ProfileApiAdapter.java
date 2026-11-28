package com.cineconecta.platform.accounts.internal.service;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.persistence.ProfileRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementación real de {@link ProfileApi} para los demás módulos (billing, viewing). Reemplaza
 * el respaldo temporal sobre JdbcTemplate.
 */
public class ProfileApiAdapter implements ProfileApi {

    private final ProfileRepository profiles;

    public ProfileApiAdapter(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileView> find(int profileId) {
        return profiles.findById(profileId).map(ProfileService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveProfiles(int accountId) {
        return profiles.countActive(accountId);
    }
}
