package com.cineconecta.platform.accounts.internal.security;

import com.cineconecta.platform.accounts.internal.service.AccountService;
import com.cineconecta.platform.accounts.internal.web.AuthController;
import com.cineconecta.platform.backoffice.internal.security.AdminSecurityConfig;
import com.cineconecta.platform.backoffice.internal.security.AdminUserStore;
import com.cineconecta.platform.backoffice.internal.web.AdminController;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Carga las dos cadenas reales (administradores {@code @Order(1)} y clientes {@code @Order(2)}) en
 * el mismo contexto: verifica que arrancan juntas y que cada ruta la atiende la cadena correcta.
 */
@WebMvcTest(controllers = {AuthController.class, AdminController.class})
@Import({SharedConfig.class, JwtSecurityConfig.class, AdminSecurityConfig.class, CustomerSecurityConfig.class,
        CustomerTokens.class})
class SecurityChainsCoexistenceWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService tokens;

    @MockitoBean
    private AccountService accountService;
    @MockitoBean
    private AdminUserStore adminUsers;

    @Test
    void adminRoutesUseTheAdminChain() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(redirectedUrl("/admin/login"));
        mockMvc.perform(get("/admin/login")).andExpect(status().isOk());
    }

    @Test
    void customerRoutesUseTheCustomerChain() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/billing/history")).andExpect(redirectedUrl("/login?next=/billing/history"));
    }

    @Test
    void customerTokenDoesNotOpenTheAdminPanel() throws Exception {
        String token = tokens.issue(TokenType.CUSTOMER, "42", Map.of(CineClaims.ACCOUNT_ID, 42)).value();

        mockMvc.perform(get("/admin").cookie(new Cookie(TokenType.CUSTOMER.cookieName(), token)))
                .andExpect(redirectedUrl("/admin/login"));
    }
}
