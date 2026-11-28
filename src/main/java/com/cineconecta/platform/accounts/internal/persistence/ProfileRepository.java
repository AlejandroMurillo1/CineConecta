package com.cineconecta.platform.accounts.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<ProfileEntity, Integer> {

    /** Siempre se busca junto con la cuenta: un perfil de otra cuenta "no existe" para el usuario. */
    Optional<ProfileEntity> findByProfileIdAndAccountId(int profileId, int accountId);

    List<ProfileEntity> findByAccountIdOrderByNameAsc(int accountId);

    @Query("SELECT count(p) FROM ProfileEntity p WHERE p.accountId = :accountId AND p.isActive = 'Y'")
    int countActive(@Param("accountId") int accountId);

    /** Usa el índice ux_profiles_name_ci (accountId, LOWER(name)). */
    @Query("""
            SELECT count(p) > 0 FROM ProfileEntity p
            WHERE p.accountId = :accountId AND lower(p.name) = lower(:name)
            """)
    boolean existsNameInAccount(@Param("accountId") int accountId, @Param("name") String name);

    /** Igual que {@link #existsNameInAccount}, ignorando el perfil que se está editando. */
    @Query("""
            SELECT count(p) > 0 FROM ProfileEntity p
            WHERE p.accountId = :accountId AND lower(p.name) = lower(:name) AND p.profileId <> :profileId
            """)
    boolean existsOtherWithName(@Param("accountId") int accountId,
                                @Param("name") String name,
                                @Param("profileId") int profileId);
}
