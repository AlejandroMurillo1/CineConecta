package com.cineconecta.platform.viewing.internal.web;

import com.cineconecta.platform.accounts.CurrentProfile;
import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.accounts.internal.security.ProfileSelectionRequiredException;
import com.cineconecta.platform.accounts.internal.web.ProfileGuardAdvice;
import com.cineconecta.platform.shared.PermitAllSecurityTestConfig;
import com.cineconecta.platform.viewing.internal.service.MyListService;
import com.cineconecta.platform.viewing.internal.service.PlaybackService;
import com.cineconecta.platform.viewing.internal.service.ViewingAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
        when(myList.list(7)).thenReturn(List.of(new MyListService.SavedTitle("c1", "Matrix", "PUBLISHED")));

        mockMvc.perform(get("/viewing/my-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Matrix")));
        verify(myList).list(7);
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

    @Test
    void oldUrlsWithProfileIdNoLongerExist() throws Exception {
        mockMvc.perform(get("/viewing/profiles/7/my-list")).andExpect(status().isNotFound());
    }
}
