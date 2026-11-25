package com.cineconecta.platform.accounts.internal;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.accounts.ProfileView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Respaldo temporal de {@link ProfileApi} sobre PostgreSQL mientras Persona 1 entrega el módulo accounts.
 */
@Configuration
public class AccountsFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(ProfileApi.class)
    public ProfileApi fallbackProfileApi(JdbcTemplate jdbcTemplate) {
        return new ProfileApi() {
            @Override
            public Optional<ProfileView> find(int profileId) {
                List<ProfileView> rows = jdbcTemplate.query(
                        """
                        SELECT profileid, accountid, name, maxagerating, isactive
                        FROM cineconecta.profiles
                        WHERE profileid = ?
                        """,
                        (rs, rowNum) -> new ProfileView(
                                rs.getInt("profileid"),
                                rs.getInt("accountid"),
                                rs.getString("name"),
                                rs.getInt("maxagerating"),
                                "Y".equalsIgnoreCase(rs.getString("isactive"))
                        ),
                        profileId
                );
                return rows.stream().findFirst();
            }

            @Override
            public int countActiveProfiles(int accountId) {
                Integer count = jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM cineconecta.profiles
                        WHERE accountid = ?
                          AND isactive = 'Y'
                        """,
                        Integer.class,
                        accountId
                );
                return count != null ? count : 0;
            }
        };
    }
}
