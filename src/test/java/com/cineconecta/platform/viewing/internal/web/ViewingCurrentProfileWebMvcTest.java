package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.accounts.CurrentProfile;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.security.ProfileSelectionRequiredException;
import com.cineconecta.platform.accounts.internal.web.ProfileGuardAdvice;
import com.cineconecta.platform.shared.PermitAllSecurityTestConfig;
import com.cineconecta.platform.viewing.internal.service.MyListService;
import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import com.cineconecta.platform.viewing.internal.service.PlaybackSession;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.PlayableRef;
import com.cineconecta.platform.viewing.internal.service.ViewingAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las pantallas de viewing usan el perfil en uso de la sesión, no un id en la URL. */
@WebMvcTest(controllers = {MyListController.class, ContinueWatchingController.class, PlaybackController.class})
@Import({PermitAllSecurityTestConfig.class, ProfileGuardAdvice.class})
class ViewingCurrentProfileWebMvcTest {

    private static final ProfileView ANA = new ProfileView(7, 42, "Ana", 12, true);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentProfile currentProfile;
    @MockitoBean
    private MyListService myList;
    @MockitoBean
    private PlaybackService playback;
    @MockitoBean
    private ViewingAccess access;

    @Test
    void myListUsesTheProfileInUse() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);
        when(myList.list(7)).thenReturn(List.of(new MyListService.SavedTitle("c1", "Matrix", "", ContentType.MOVIE, "/images/posters/c1.svg", true)));

        mockMvc.perform(get("/viewing/my-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Matrix")));
        verify(myList).list(7);
    }

    @Test
    void myListShowsCardsWithPosterTypeAndRemoveButton() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);
        when(myList.list(7)).thenReturn(List.of(
                new MyListService.SavedTitle("6740a1f0c3b2a1d4e5f60002", "Noche en San Antonio", "",
                        ContentType.MOVIE, "/images/posters/6740a1f0c3b2a1d4e5f60002.svg", true),
                new MyListService.SavedTitle("6740a1f0c3b2a1d4e5f60003", "Páramos", "Retirado",
                        ContentType.DOCUMENTARY, null, true),
                new MyListService.SavedTitle("6740a1f0c3b2a1d4e5f60009", "Título no disponible", "",
                        null, null, false)));

        mockMvc.perform(get("/viewing/my-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cw-card")))
                .andExpect(content().string(containsString("src=\"/images/posters/6740a1f0c3b2a1d4e5f60002.svg\"")))
                .andExpect(content().string(containsString("href=\"/catalog/6740a1f0c3b2a1d4e5f60002\"")))
                .andExpect(content().string(containsString("Película")))
                .andExpect(content().string(containsString("Documental")))
                .andExpect(content().string(containsString("Retirado")))
                .andExpect(content().string(containsString("✕ Quitar")))
                // El no disponible no enlaza al detalle, y Mi lista ya no enlaza a "Continuar viendo".
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/catalog/6740a1f0c3b2a1d4e5f60009\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/viewing/continue-watching"))));
    }

    @Test
    void emptyMyListInvitesToExploreTheCatalog() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);
        when(myList.list(7)).thenReturn(List.of());

        mockMvc.perform(get("/viewing/my-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no has guardado títulos.")))
                .andExpect(content().string(containsString("Explorar el catálogo")));
    }

    @Test
    void addingToMyListUsesTheProfileInUse() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);

        mockMvc.perform(post("/viewing/my-list").with(csrf()).param("contentId", "c1"))
                .andExpect(redirectedUrl("/viewing/my-list"));
        verify(myList).add(7, "c1");
    }

    @Test
    void withoutProfileInUseViewingPagesAskToChooseOne() throws Exception {
        when(currentProfile.require()).thenThrow(new ProfileSelectionRequiredException());

        mockMvc.perform(get("/viewing/my-list")).andExpect(redirectedUrl("/profiles?next=/viewing/my-list"));
        mockMvc.perform(get("/viewing/continue-watching"))
                .andExpect(redirectedUrl("/profiles?next=/viewing/continue-watching"));
        mockMvc.perform(post("/viewing/player").with(csrf()).param("contentId", "c1"))
                .andExpect(redirectedUrl("/profiles"));
        verify(playback, never()).start(anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    // "Continuar viendo" en la página de inicio (sección cargada con htmx)

    private static final PlaybackService.ContinueItem EPISODE = new PlaybackService.ContinueItem(
            "6740a1f0c3b2a1d4e5f600a1", "6740a1f0c3b2a1d4e5f60001", "Río Abajo", "La subasta",
            1, 2, 1410, 2820, "/images/posters/6740a1f0c3b2a1d4e5f60001.svg");
    private static final PlaybackService.ContinueItem MOVIE = new PlaybackService.ContinueItem(
            "6740a1f0c3b2a1d4e5f600a2", "6740a1f0c3b2a1d4e5f60002", "Noche en San Antonio", null,
            null, null, 600, 6240, "/images/posters/6740a1f0c3b2a1d4e5f60002.svg");

    @Test
    void homeSectionShowsPendingTitlesOfTheProfileInUse() throws Exception {
        when(currentProfile.current()).thenReturn(Optional.of(ANA));
        when(playback.continueWatching(7)).thenReturn(List.of(EPISODE, MOVIE));

        mockMvc.perform(get("/viewing/continue-watching/home-section").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Continuar viendo")))
                .andExpect(content().string(containsString("Río Abajo")))
                .andExpect(content().string(containsString("T1 · E2")))
                .andExpect(content().string(containsString("width:50%")))
                .andExpect(content().string(containsString("Te quedan 24 min")))
                .andExpect(content().string(containsString("Noche en San Antonio")))
                .andExpect(content().string(containsString("action=\"/viewing/continue-watching/6740a1f0c3b2a1d4e5f600a1\"")))
                .andExpect(content().string(containsString("href=\"/catalog/6740a1f0c3b2a1d4e5f60002\"")))
                // Es un fragmento: sin la página completa alrededor.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<html"))));
    }

    @Test
    void homeSectionIsEmptyWithoutProfileInUseAndDoesNotRedirect() throws Exception {
        when(currentProfile.current()).thenReturn(Optional.empty());

        mockMvc.perform(get("/viewing/continue-watching/home-section"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("cw-"))));
        verify(playback, never()).continueWatching(anyInt());
    }

    @Test
    void homeSectionIsEmptyWithoutAValidSubscription() throws Exception {
        when(currentProfile.current()).thenReturn(Optional.of(ANA));
        when(playback.continueWatching(7))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Necesitas una suscripción vigente"));

        mockMvc.perform(get("/viewing/continue-watching/home-section"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("cw-"))));
    }

    @Test
    void homeSectionIsEmptyWhenNothingIsPending() throws Exception {
        when(currentProfile.current()).thenReturn(Optional.of(ANA));
        when(playback.continueWatching(7)).thenReturn(List.of());

        mockMvc.perform(get("/viewing/continue-watching/home-section"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("cw-section"))));
    }

    @Test
    void continueWatchingPageUsesTheSameCards() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);
        when(playback.continueWatching(7)).thenReturn(List.of(EPISODE));

        mockMvc.perform(get("/viewing/continue-watching"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")))
                .andExpect(content().string(containsString("cw-card")))
                .andExpect(content().string(containsString("Río Abajo")));
    }

    @Test
    void oldUrlsWithProfileIdNoLongerExist() throws Exception {
        mockMvc.perform(get("/viewing/profiles/7/my-list")).andExpect(status().isNotFound());
    }

    @Test
    void playerShowsAVideoAndTimesAsHoursMinutesSeconds() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);
        Instant now = Instant.parse("2026-11-27T20:00:00Z");
        var session = new PlaybackSession("6740a1f0c3b2a1d4e5f60099", 42, 7, "6740a1f0c3b2a1d4e5f60002", null,
                ContentType.MOVIE, 2, 6300, now, null, null, 750, 750, false, 0, now);
        when(playback.find(7, session.id())).thenReturn(session);
        when(playback.currentPosition(session)).thenReturn(750);
        when(playback.describe(session)).thenReturn(Optional.of(new PlayableRef(session.contentId(), null,
                ContentType.MOVIE, "Noche en San Antonio", null, null, null, 6300, 16, "poster.jpg")));

        mockMvc.perform(get("/viewing/player/" + session.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<video")))
                .andExpect(content().string(containsString("Noche en San Antonio")))
                .andExpect(content().string(containsString("00:12:30")))
                .andExpect(content().string(containsString("01:45:00")));
    }

    @Test
    void seekAcceptsHoursMinutesSeconds() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);

        mockMvc.perform(post("/viewing/player/s1").with(csrf()).param("action", "SEEK").param("position", "00:01:30"))
                .andExpect(redirectedUrl("/viewing/player/s1"));
        verify(playback).apply(7, "s1", PlaybackSession.Action.SEEK, 90);
    }

    @Test
    void closedSessionGoesBackToTheDetailInsteadOfADeadPlayer() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);
        Instant now = Instant.parse("2026-11-27T20:00:00Z");
        var closed = new PlaybackSession("6740a1f0c3b2a1d4e5f60098", 42, 7, "6740a1f0c3b2a1d4e5f60002", null,
                ContentType.MOVIE, 2, 6300, now, null, now.plusSeconds(6300), 6300, 6300, true, 3, now);
        when(playback.find(7, closed.id())).thenReturn(closed);

        mockMvc.perform(get("/viewing/player/" + closed.id()))
                .andExpect(redirectedUrl("/catalog/6740a1f0c3b2a1d4e5f60002"));
    }

    @Test
    void finishingAtTheEndGoesToTheDetailWithAMessage() throws Exception {
        when(currentProfile.require()).thenReturn(ANA);
        Instant now = Instant.parse("2026-11-27T20:00:00Z");
        var closed = new PlaybackSession("s2", 42, 7, "6740a1f0c3b2a1d4e5f60002", null,
                ContentType.MOVIE, 2, 6300, now, null, now.plusSeconds(6300), 6300, 6300, true, 3, now);
        when(playback.find(7, "s2")).thenReturn(closed);
        when(playback.describe(closed)).thenReturn(Optional.of(new PlayableRef(closed.contentId(), null,
                ContentType.MOVIE, "Noche en San Antonio", null, null, null, 6300, 16, "poster.jpg")));

        mockMvc.perform(post("/viewing/player/s2").with(csrf()).param("action", "FINISH"))
                .andExpect(redirectedUrl("/catalog/6740a1f0c3b2a1d4e5f60002"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                        .attribute("success", "Terminaste de ver «Noche en San Antonio»."));
        verify(playback).apply(7, "s2", PlaybackSession.Action.FINISH, null);
    }
}
