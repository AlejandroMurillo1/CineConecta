package com.cineconecta.platform.accounts.internal.service;

import com.cineconecta.platform.accounts.ProfileEntitlements;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.persistence.ProfileEntity;
import com.cineconecta.platform.accounts.internal.persistence.ProfileRepository;
import com.cineconecta.platform.accounts.internal.service.ProfileRuleException.Reason;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Gestión de perfiles de una cuenta (RF-05 a RF-09). Todas las operaciones reciben el
 * {@code accountId} de la sesión, nunca del formulario, y buscan el perfil junto con la cuenta: un
 * perfil ajeno se trata como inexistente.
 */
public class ProfileService {

    public static final int MIN_AGE_RATING = 0;
    public static final int MAX_AGE_RATING = 18;

    private final ProfileRepository profiles;
    private final ProfileEntitlements entitlements;
    private final AccountLock accountLock;

    public ProfileService(ProfileRepository profiles, ProfileEntitlements entitlements, AccountLock accountLock) {
        this.profiles = Objects.requireNonNull(profiles);
        this.entitlements = Objects.requireNonNull(entitlements);
        this.accountLock = Objects.requireNonNull(accountLock);
    }

    /** Resumen para la pantalla de perfiles: lista, cupo del plan vigente (0 = sin suscripción). */
    @Transactional(readOnly = true)
    public ProfileOverview overview(int accountId) {
        List<ProfileView> all = profiles.findByAccountIdOrderByNameAsc(accountId).stream()
                .map(ProfileService::toView)
                .toList();
        int active = (int) all.stream().filter(ProfileView::active).count();
        return new ProfileOverview(all, active, entitlements.maxProfilesFor(accountId));
    }

    @Transactional(readOnly = true)
    public Optional<ProfileView> find(int accountId, int profileId) {
        return profiles.findByProfileIdAndAccountId(profileId, accountId).map(ProfileService::toView);
    }

    /** RF-05: requiere suscripción vigente y cupo en el plan. */
    @Transactional
    public ProfileView create(int accountId, String name, int maxAgeRating) {
        String cleanName = validName(name);
        validRating(maxAgeRating);
        accountLock.lock(accountId);
        requireRoomForOneMoreActive(accountId);
        if (profiles.existsNameInAccount(accountId, cleanName)) {
            throw duplicateName(cleanName);
        }
        return toView(saveHandlingDuplicate(new ProfileEntity(accountId, cleanName, maxAgeRating), cleanName));
    }

    /** RF-06: nombre y clasificación. No exige suscripción (no aumenta el número de perfiles activos). */
    @Transactional
    public ProfileView update(int accountId, int profileId, String name, int maxAgeRating) {
        String cleanName = validName(name);
        validRating(maxAgeRating);
        ProfileEntity profile = owned(accountId, profileId);
        if (profiles.existsOtherWithName(accountId, cleanName, profileId)) {
            throw duplicateName(cleanName);
        }
        profile.rename(cleanName);
        profile.changeMaxAgeRating(maxAgeRating);
        return toView(saveHandlingDuplicate(profile, cleanName));
    }

    /** RF-07: desactivar siempre se permite; la actividad histórica se conserva (no se borra nada). */
    @Transactional
    public ProfileView deactivate(int accountId, int profileId) {
        ProfileEntity profile = owned(accountId, profileId);
        profile.deactivate();
        return toView(profiles.saveAndFlush(profile));
    }

    /** RF-07: reactivar cuenta como un perfil activo más, así que exige suscripción y cupo. */
    @Transactional
    public ProfileView reactivate(int accountId, int profileId) {
        accountLock.lock(accountId);
        ProfileEntity profile = owned(accountId, profileId);
        if (profile.isActive()) {
            return toView(profile);
        }
        requireRoomForOneMoreActive(accountId);
        profile.activate();
        return toView(profiles.saveAndFlush(profile));
    }

    /** RF-09: solo se pueden seleccionar perfiles activos de la propia cuenta. */
    @Transactional(readOnly = true)
    public Optional<ProfileView> selectable(int accountId, int profileId) {
        return find(accountId, profileId).filter(ProfileView::active);
    }

    // ---------- reglas ----------

    private void requireRoomForOneMoreActive(int accountId) {
        int max = entitlements.maxProfilesFor(accountId);
        if (max <= 0) {
            throw new ProfileRuleException(Reason.NO_ACTIVE_SUBSCRIPTION,
                    "Necesitas una suscripción vigente para tener perfiles activos.");
        }
        int active = profiles.countActive(accountId);
        if (active >= max) {
            throw new ProfileRuleException(Reason.PROFILE_LIMIT_REACHED,
                    "Tu plan permite " + max + " perfil(es) activo(s) y ya tienes " + active
                            + ". Desactiva uno o cambia de plan.");
        }
    }

    private ProfileEntity owned(int accountId, int profileId) {
        return profiles.findByProfileIdAndAccountId(profileId, accountId)
                .orElseThrow(() -> new ProfileRuleException(Reason.NOT_FOUND, "El perfil no existe."));
    }

    private ProfileEntity saveHandlingDuplicate(ProfileEntity profile, String name) {
        try {
            // saveAndFlush: el índice ux_profiles_name_ci resuelve dos peticiones simultáneas con el mismo nombre.
            return profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException duplicate) {
            throw duplicateName(name);
        }
    }

    private static ProfileRuleException duplicateName(String name) {
        return new ProfileRuleException(Reason.DUPLICATE_NAME,
                "Ya tienes un perfil llamado \"" + name + "\".");
    }

    private static String validName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > ProfileEntity.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "El nombre debe tener entre 1 y " + ProfileEntity.MAX_NAME_LENGTH + " caracteres.");
        }
        return clean;
    }

    private static void validRating(int rating) {
        if (rating < MIN_AGE_RATING || rating > MAX_AGE_RATING) {
            throw new IllegalArgumentException("La clasificación debe estar entre 0 y 18.");
        }
    }

    static ProfileView toView(ProfileEntity e) {
        return new ProfileView(e.getProfileId(), e.getAccountId(), e.getName(), e.getMaxAgeRating(), e.isActive());
    }
}
