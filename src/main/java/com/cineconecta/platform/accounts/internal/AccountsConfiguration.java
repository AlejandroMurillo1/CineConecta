package com.cineconecta.platform.accounts.internal;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.accounts.ProfileEntitlements;
import com.cineconecta.platform.accounts.internal.persistence.AccountRepository;
import com.cineconecta.platform.accounts.internal.persistence.ProfileRepository;
import com.cineconecta.platform.accounts.internal.service.AccountLock;
import com.cineconecta.platform.accounts.internal.service.AccountService;
import com.cineconecta.platform.accounts.internal.service.ProfileApiAdapter;
import com.cineconecta.platform.accounts.internal.service.ProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;

/** Servicios del módulo accounts. */
@Configuration(proxyBeanMethods = false)
public class AccountsConfiguration {

    /**
     * BCrypt local al módulo (no se publica un {@code PasswordEncoder} global para no interferir
     * con la autenticación de administradores, que usa el suyo).
     */
    @Bean
    public AccountService accountService(AccountRepository accounts, Clock clock) {
        return new AccountService(accounts, new BCryptPasswordEncoder(), clock);
    }

    @Bean
    public ProfileService profileService(ProfileRepository profiles, ProfileEntitlements entitlements,
                                         JdbcTemplate jdbc) {
        return new ProfileService(profiles, entitlements, new AccountLock(jdbc));
    }

    @Bean
    public ProfileApi profileApi(ProfileRepository profiles) {
        return new ProfileApiAdapter(profiles);
    }
}
