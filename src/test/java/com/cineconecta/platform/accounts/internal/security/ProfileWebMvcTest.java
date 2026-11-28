package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.accounts.ProfileEntitlements;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.service.ProfileOverview;
import com.cineconecta.platform.accounts.internal.service.ProfileRuleException;
import com.cineconecta.platform.accounts.internal.service.ProfileRuleException.Reason;
import com.cineconecta.platform.accounts.internal.service.ProfileService;
import com.cineconecta.platform.accounts.internal.web.ProfileController;
import com.cineconecta.platform.accounts.internal.web.ProfileGuardAdvice;
import com.cineconecta.platform.catalog.ViewerContext;
import com.cineconecta.platform.shared.config.SharedConfig;
import com.cineconecta.platform.shared.security.CineClaims;
import com.cineconecta.platform.shared.security.JwtSecurityConfig;
import com.cineconecta.platform.shared.security.JwtTokenService;
import com.cineconecta.platform.shared.security.TokenType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProfileController.class, ProfileWebMvcTest.CatalogProbeController.class})
@Import({SharedConfig.class, JwtSecurityConfig.class, CustomerSecurityConfig.class, JwtCurrentAccountProvider.class,
        CustomerTokens.class, CurrentProfileResolver.class, ProfileViewerContext.class, ProfileGuardAdvice.class,
        ProfileWebMvcTest.CatalogProbeController.class})
class ProfileWebMvcTest {

    private static final int ACCOUNT = 42;
    private static final ProfileView ANA = new ProfileView(7, ACCOUNT, "Ana", 12, true);
    private static final ProfileView LEO = new ProfileView(8, ACCOUNT, "Leo", 0, false);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService tokens;

    @MockitoBean
    private ProfileService profileService;
    @MockitoBean
    private ProfileEntitlements entitlements;

    /** Simula una pantalla del catálogo: solo usa el puerto ViewerContext, como CatalogController. */
    @RestController
    static class CatalogProbeController {
        private final ViewerContext viewer;

        CatalogProbeController(ViewerContext viewer) {
            this.viewer = viewer;
        }

        @GetMapping("/test/catalog")
        String catalog() {
            return "rating=" + viewer.maxAgeRating();
        }

        @GetMapping("/test/catalog-section")
        String section() {
            var rating = viewer.allowedMaxAgeRating();
            return rating.isPresent() ? "rating=" + rating.getAsInt() : "none";
        }
    }

    private Cookie cookie(Integer profileId) {
        Map<String, Object> claims = profileId == null
                ? Map.of(CineClaims.ACCOUNT_ID, ACCOUNT)
                : Map.of(CineClaims.ACCOUNT_ID, ACCOUNT, CineClaims.PROFILE_ID, profileId);
        return new Cookie("CINE_TOKEN", tokens.issue(TokenType.CUSTOMER, "42", claims).value());
    }

    private Jwt decodeSetCookie(MvcResult result) {
        String value = result.getResponse().getCookie("CINE_TOKEN").getValue();
        return tokens.decoderFor(TokenType.CUSTOMER).decode(value);
    }

    // ---------- listado y selección ----------

    @Test
    void profilesPageRequiresLogin() throws Exception {
        mockMvc.perform(get("/profiles")).andExpect(redirectedUrl("/login?next=/profiles"));
    }

    @Test
    void listsActiveAndInactiveProfilesWithQuota() throws Exception {
        when(profileService.overview(ACCOUNT)).thenReturn(new ProfileOverview(List.of(ANA, LEO), 1, 3));

        mockMvc.perform(get("/profiles").cookie(cookie(null)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("¿Quién está viendo?")))
                .andExpect(content().string(containsString("Hasta 12 años")))
                .andExpect(content().string(containsString("Perfiles desactivados")))
                .andExpect(content().string(containsString("Crear perfil")));
    }

    @Test
    void withoutSubscriptionTheCreateFormIsHidden() throws Exception {
        when(profileService.overview(ACCOUNT)).thenReturn(new ProfileOverview(List.of(), 0, 0));

        mockMvc.perform(get("/profiles").cookie(cookie(null)))
                .andExpect(content().string(containsString("No tienes una suscripción vigente")))
                .andExpect(content().string(not(containsString("Crear perfil"))));
    }

    @Test
    void selectingAnActiveProfileReissuesTokenWithProfileId() throws Exception {
        when(profileService.selectable(ACCOUNT, 7)).thenReturn(Optional.of(ANA));

        MvcResult result = mockMvc.perform(post("/profiles/7/select").with(csrf()).cookie(cookie(null))
                        .param("next", "/catalog?q=matrix"))
                .andExpect(redirectedUrl("/catalog?q=matrix"))
                .andReturn();

        Jwt jwt = decodeSetCookie(result);
        assertThat(((Number) jwt.getClaim(CineClaims.PROFILE_ID)).intValue()).isEqualTo(7);
        assertThat(((Number) jwt.getClaim(CineClaims.ACCOUNT_ID)).intValue()).isEqualTo(ACCOUNT);
    }

    @Test
    void inactiveOrForeignProfileCannotBeSelected() throws Exception {
        when(profileService.selectable(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(profileService.overview(ACCOUNT)).thenReturn(new ProfileOverview(List.of(LEO), 0, 3));

        mockMvc.perform(post("/profiles/999/select").with(csrf()).cookie(cookie(null)))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string(containsString("Ese perfil no está disponible")));
    }

    // ---------- crear / editar / desactivar ----------

    @Test
    void createShowsRuleErrorFromService() throws Exception {
        when(profileService.overview(ACCOUNT)).thenReturn(new ProfileOverview(List.of(ANA), 1, 1));
        when(profileService.create(ACCOUNT, "Leo", 7)).thenThrow(new ProfileRuleException(
                Reason.PROFILE_LIMIT_REACHED, "Tu plan permite 1 perfil(es) activo(s) y ya tienes 1."));

        mockMvc.perform(post("/profiles").with(csrf()).cookie(cookie(null))
                        .param("name", "Leo").param("maxAgeRating", "7"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Tu plan permite 1 perfil(es)")));
    }

    @Test
    void createValidatesInputBeforeCallingTheService() throws Exception {
        when(profileService.overview(ACCOUNT)).thenReturn(new ProfileOverview(List.of(), 0, 3));

        mockMvc.perform(post("/profiles").with(csrf()).cookie(cookie(null))
                        .param("name", " ").param("maxAgeRating", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escribe un nombre para el perfil")))
                .andExpect(content().string(containsString("entre 0 y 18")));
        verify(profileService, never()).create(anyInt(), anyString(), anyInt());
    }

    @Test
    void profileOfAnotherAccountIsNotFound() throws Exception {
        when(profileService.find(ACCOUNT, 500)).thenReturn(Optional.empty());

        mockMvc.perform(get("/profiles/500/edit").cookie(cookie(null))).andExpect(status().isNotFound());
        mockMvc.perform(post("/profiles/500/deactivate").with(csrf()).cookie(cookie(null)))
                .andExpect(status().isNotFound());
        verify(profileService, never()).deactivate(anyInt(), anyInt());
    }

    @Test
    void deactivatingTheProfileInUseRemovesItFromTheToken() throws Exception {
        when(profileService.find(ACCOUNT, 7)).thenReturn(Optional.of(ANA));
        when(profileService.selectable(ACCOUNT, 7)).thenReturn(Optional.of(ANA));

        MvcResult result = mockMvc.perform(post("/profiles/7/deactivate").with(csrf()).cookie(cookie(7)))
                .andExpect(redirectedUrl("/profiles?ok=deactivated"))
                .andReturn();

        verify(profileService).deactivate(ACCOUNT, 7);
        assertThat(decodeSetCookie(result).hasClaim(CineClaims.PROFILE_ID)).isFalse();
    }

    // ---------- ViewerContext: protección del catálogo ----------

    @Test
    void catalogWithoutSelectedProfileRedirectsToChooseOne() throws Exception {
        mockMvc.perform(get("/test/catalog?q=x").cookie(cookie(null)))
                .andExpect(redirectedUrl("/profiles?next=/test/catalog?q%3Dx"));
    }

    @Test
    void htmxCatalogRequestWithoutProfileGetsHxRedirect() throws Exception {
        mockMvc.perform(get("/test/catalog").header("HX-Request", "true").cookie(cookie(null)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("HX-Redirect", "/profiles"));
    }

    @Test
    void profileDeactivatedAfterSelectionIsNoLongerAccepted() throws Exception {
        // El token aún dice profileId=8, pero en PostgreSQL ya no está activo.
        when(profileService.selectable(ACCOUNT, 8)).thenReturn(Optional.empty());

        mockMvc.perform(get("/test/catalog").cookie(cookie(8)))
                .andExpect(redirectedUrl("/profiles?next=/test/catalog"));
    }

    @Test
    void catalogWithoutSubscriptionRedirectsToPlans() throws Exception {
        when(profileService.selectable(ACCOUNT, 7)).thenReturn(Optional.of(ANA));
        when(entitlements.maxProfilesFor(ACCOUNT)).thenReturn(0);

        mockMvc.perform(get("/test/catalog").cookie(cookie(7))).andExpect(redirectedUrl("/billing/plans"));
    }

    @Test
    void embeddedCatalogSectionNeverRedirects() throws Exception {
        // Sin perfil elegido.
        mockMvc.perform(get("/test/catalog-section").header("HX-Request", "true").cookie(cookie(null)))
                .andExpect(status().isOk())
                .andExpect(content().string("none"));

        // Con perfil, pero sin suscripción vigente.
        when(profileService.selectable(ACCOUNT, 7)).thenReturn(Optional.of(ANA));
        when(entitlements.maxProfilesFor(ACCOUNT)).thenReturn(0);
        mockMvc.perform(get("/test/catalog-section").header("HX-Request", "true").cookie(cookie(7)))
                .andExpect(status().isOk())
                .andExpect(content().string("none"));

        // Con perfil y suscripción.
        when(entitlements.maxProfilesFor(ACCOUNT)).thenReturn(3);
        mockMvc.perform(get("/test/catalog-section").cookie(cookie(7)))
                .andExpect(content().string("rating=12"));
    }

    @Test
    void catalogGetsTheRatingOfTheVerifiedProfile() throws Exception {
        when(profileService.selectable(ACCOUNT, 7)).thenReturn(Optional.of(ANA));
        when(entitlements.maxProfilesFor(ACCOUNT)).thenReturn(3);

        mockMvc.perform(get("/test/catalog").cookie(cookie(7)))
                .andExpect(status().isOk())
                .andExpect(content().string("rating=12"));
    }
}
