package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.accounts.internal.service.AccountService;
import com.cineconecta.platform.accounts.internal.service.AccountSummary;
import com.cineconecta.platform.accounts.internal.service.EmailAlreadyRegisteredException;
import com.cineconecta.platform.accounts.internal.web.AuthController;
import com.cineconecta.platform.shared.CurrentAccountProvider;
import com.cineconecta.platform.shared.config.SharedConfig;
import com.cineconecta.platform.shared.security.CineClaims;
import com.cineconecta.platform.shared.security.JwtProperties;
import com.cineconecta.platform.shared.security.JwtSecurityConfig;
import com.cineconecta.platform.shared.security.JwtTokenService;
import com.cineconecta.platform.shared.security.TokenType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, CustomerSecurityWebMvcTest.WhoAmIController.class})
@Import({SharedConfig.class, JwtSecurityConfig.class, CustomerSecurityConfig.class,
        JwtCurrentAccountProvider.class, CustomerTokens.class, CustomerSecurityWebMvcTest.WhoAmIController.class})
class CustomerSecurityWebMvcTest {

    private static final AccountSummary ANA = new AccountSummary(42, "Ana Pérez", "ana@correo.com");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService tokens;
    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private AccountService accountService;

    /** Ruta protegida de prueba que muestra la cuenta tomada del JWT. */
    @RestController
    static class WhoAmIController {
        private final CurrentAccountProvider current;

        WhoAmIController(CurrentAccountProvider current) {
            this.current = current;
        }

        @GetMapping("/test/whoami")
        String whoami() {
            return String.valueOf(current.requireCurrentAccountId());
        }
    }

    private Cookie customerCookie() {
        String token = tokens.issue(TokenType.CUSTOMER, "42", Map.of(CineClaims.ACCOUNT_ID, 42)).value();
        return new Cookie(TokenType.CUSTOMER.cookieName(), token);
    }

    // ---------- Inicio de sesión ----------

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iniciar sesión")));
    }

    @Test
    void validLoginSetsHttpOnlyCookieAndReturnsToRequestedPage() throws Exception {
        when(accountService.authenticate("ana@correo.com", "Secreta123")).thenReturn(Optional.of(ANA));

        MvcResult result = mockMvc.perform(post("/login").with(csrf())
                        .param("email", "ana@correo.com")
                        .param("password", "Secreta123")
                        .param("next", "/billing/history"))
                .andExpect(redirectedUrl("/billing/history"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("CINE_TOKEN=").contains("HttpOnly", "SameSite=Lax");
        assertThat(result.getRequest().getSession(false)).as("no debe crearse sesión HTTP").isNull();
    }

    @Test
    void loginIgnoresExternalRedirects() throws Exception {
        when(accountService.authenticate(anyString(), anyString())).thenReturn(Optional.of(ANA));

        for (String next : new String[]{"//evil.example", "https://evil.example", "/\\evil.example"}) {
            mockMvc.perform(post("/login").with(csrf())
                            .param("email", "ana@correo.com").param("password", "x").param("next", next))
                    .andExpect(redirectedUrl("/profiles"));
        }
    }

    @Test
    void wrongCredentialsShowGenericErrorWithoutCookie() throws Exception {
        when(accountService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/login").with(csrf()).param("email", "ana@correo.com").param("password", "mala"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Correo o contraseña incorrectos.")))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/login").param("email", "ana@correo.com").param("password", "Secreta123"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accountService);
    }

    // ---------- Rutas protegidas ----------

    @Test
    void protectedPageWithoutCookieRedirectsToLoginRememberingThePage() throws Exception {
        mockMvc.perform(get("/test/whoami?x=1"))
                .andExpect(redirectedUrl("/login?next=/test/whoami?x%3D1"));
    }

    @Test
    void htmxRequestWithoutCookieGets401InsteadOfRedirect() throws Exception {
        mockMvc.perform(get("/test/whoami").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCookieAuthenticatesAndExposesAccountIdFromToken() throws Exception {
        mockMvc.perform(get("/test/whoami").cookie(customerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void expiredCookieIsClearedAndUserIsSentToLogin() throws Exception {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        JwtTokenService past = new JwtTokenService(jwtProperties, Clock.fixed(twoHoursAgo, ZoneOffset.UTC));
        String expired = past.issue(TokenType.CUSTOMER, "42", Map.of(CineClaims.ACCOUNT_ID, 42)).value();

        MvcResult result = mockMvc.perform(get("/test/whoami").cookie(new Cookie("CINE_TOKEN", expired)))
                .andExpect(redirectedUrl("/login?expired&next=/test/whoami"))
                .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith("CINE_TOKEN=;").contains("Max-Age=0");
    }

    @Test
    void adminTokenPlacedInCustomerCookieIsRejected() throws Exception {
        String admin = tokens.issue(TokenType.ADMIN, "admin@cineconecta.local", Map.of(CineClaims.ACCOUNT_ID, 42)).value();

        mockMvc.perform(get("/test/whoami").cookie(new Cookie("CINE_TOKEN", admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired&next=/test/whoami"));
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/logout").with(csrf()).cookie(customerCookie()))
                .andExpect(redirectedUrl("/login?logout"))
                .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith("CINE_TOKEN=;").contains("Max-Age=0");
    }

    // ---------- Registro ----------

    @Test
    void registerShowsValidationErrors() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "")
                        .param("email", "no-es-correo")
                        .param("password", "corta")
                        .param("confirmPassword", "otra"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escribe tu nombre completo")))
                .andExpect(content().string(containsString("El correo no es válido")))
                .andExpect(content().string(containsString("entre 8 y 72 caracteres")))
                .andExpect(content().string(containsString("Las contraseñas no coinciden")));
        verifyNoInteractions(accountService);
    }

    @Test
    void registerWithDuplicateEmailShowsFieldError() throws Exception {
        when(accountService.register("Ana", "ana@correo.com", "Secreta123"))
                .thenThrow(new EmailAlreadyRegisteredException("ana@correo.com"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Ana").param("email", "ana@correo.com")
                        .param("password", "Secreta123").param("confirmPassword", "Secreta123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Ya existe una cuenta con este correo.")))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void successfulRegistrationSignsInAndGoesToPlans() throws Exception {
        when(accountService.register("Ana Pérez", "ana@correo.com", "Secreta123")).thenReturn(ANA);

        MvcResult result = mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Ana Pérez").param("email", "ana@correo.com")
                        .param("password", "Secreta123").param("confirmPassword", "Secreta123"))
                .andExpect(redirectedUrl("/billing/plans"))
                .andReturn();
        String token = result.getResponse().getCookie("CINE_TOKEN").getValue();

        mockMvc.perform(get("/test/whoami").cookie(new Cookie("CINE_TOKEN", token)))
                .andExpect(content().string("42"));
    }
}
