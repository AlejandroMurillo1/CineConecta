package com.cineconecta.platform.backoffice;

import com.cineconecta.platform.backoffice.internal.security.AdminAccount;
import com.cineconecta.platform.backoffice.internal.security.AdminPrincipal;
import com.cineconecta.platform.backoffice.internal.security.AdminSecurityConfig;
import com.cineconecta.platform.backoffice.internal.security.AdminUserStore;
import com.cineconecta.platform.backoffice.internal.web.AdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminController.class)
@Import(AdminSecurityConfig.class)
class AdminSecurityWebMvcTest {

    private static final String EMAIL = "admin@cineconecta.local";
    private static final String PASSWORD = "Admin123*";
    private static final AdminAccount ADMIN = new AdminAccount(
            "6740a1f0c3b2a1d4e5f60001", "Ana Admin", EMAIL, new BCryptPasswordEncoder().encode(PASSWORD));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserStore adminUsers;

    @BeforeEach
    void setUp() {
        when(adminUsers.findByEmail(anyString())).thenReturn(Optional.empty());
        when(adminUsers.findByEmail(EMAIL)).thenReturn(Optional.of(ADMIN));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("backoffice/login"));
    }

    @Test
    void protectedAdminPagesRedirectToLogin() throws Exception {
        mockMvc.perform(get("/admin/reports/revenue"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void validCredentialsAuthenticateAsAdmin() throws Exception {
        mockMvc.perform(post("/admin/login").param("email", EMAIL).param("password", PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(authenticated().withUsername(EMAIL).withRoles("ADMIN"));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/admin/login").param("email", "Admin@CineConecta.local").param("password", PASSWORD).with(csrf()))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/admin/login").param("email", EMAIL).param("password", "otra").with(csrf()))
                .andExpect(redirectedUrl("/admin/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void unknownEmailIsRejectedTheSameWay() throws Exception {
        mockMvc.perform(post("/admin/login").param("email", "nadie@cineconecta.local").param("password", PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/admin/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/admin/login").param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersWithoutAdminRoleAreForbidden() throws Exception {
        mockMvc.perform(get("/admin").with(user("cliente@cineconecta.local").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesTheHomePageWithTheirName() throws Exception {
        mockMvc.perform(get("/admin").with(user(new AdminPrincipal(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(view().name("backoffice/home"))
                .andExpect(content().string(containsString("Ana Admin")));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        mockMvc.perform(post("/admin/logout").with(user(new AdminPrincipal(ADMIN))).with(csrf()))
                .andExpect(redirectedUrl("/admin/login?logout"))
                .andExpect(unauthenticated());
    }
}
