package com.cineconecta.platform.billing;

import com.cineconecta.platform.billing.internal.exceptions.DuplicatePlanNameException;
import com.cineconecta.platform.billing.internal.exceptions.PlanNotFoundException;
import com.cineconecta.platform.billing.internal.web.AdminPlansController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pantallas de gestión de planes (RF-12.1 a RF-12.4). La exigencia del rol ADMIN se prueba en
 * {@code AdminSecurityWebMvcTest}; aquí se usa la cadena que deja pasar todo, con CSRF activo.
 */
@WebMvcTest(AdminPlansController.class)
@Import({com.cineconecta.platform.shared.config.SharedConfig.class,
        com.cineconecta.platform.shared.PermitAllSecurityTestConfig.class})
class AdminPlansWebMvcTest {

    private static final AdminPlanView BASIC =
            new AdminPlanView(1, "Básico", new BigDecimal("16900.00"), 1, true, 4);
    private static final AdminPlanView LEGACY =
            new AdminPlanView(9, "Antiguo", new BigDecimal("9900.00"), 2, false, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanApi planApi;

    @Test
    void listsActiveAndInactivePlansWithTheirSubscriptions() throws Exception {
        when(planApi.listPlansForAdmin()).thenReturn(List.of(LEGACY, BASIC));

        mockMvc.perform(get("/admin/plans"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/admin-plans"))
                .andExpect(content().string(containsString("value=\"Básico\"")))
                .andExpect(content().string(containsString("16.900")))
                .andExpect(content().string(containsString("Inactivo")))
                .andExpect(content().string(containsString("action=\"/admin/plans/1/deactivate\"")))
                .andExpect(content().string(containsString("action=\"/admin/plans/9/activate\"")))
                .andExpect(content().string(containsString("href=\"/admin/plans\"")));
    }

    @Test
    void createsAPlanAndRedirectsWithConfirmation() throws Exception {
        when(planApi.createPlan("Familiar", new BigDecimal("45900"), 6))
                .thenReturn(new AdminPlanView(4, "Familiar", new BigDecimal("45900.00"), 6, true, 0));

        mockMvc.perform(post("/admin/plans").with(csrf())
                        .param("name", "Familiar").param("price", "45900").param("maxProfiles", "6"))
                .andExpect(redirectedUrl("/admin/plans"))
                .andExpect(flash().attribute("success", containsString("Familiar")));
    }

    @Test
    void invalidFormIsShownAgainWithoutCallingTheApi() throws Exception {
        when(planApi.listPlansForAdmin()).thenReturn(List.of(BASIC));

        mockMvc.perform(post("/admin/plans").with(csrf())
                        .param("name", " ").param("price", "0").param("maxProfiles", "11"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/admin-plans"))
                .andExpect(model().attributeHasFieldErrors("form", "name", "price", "maxProfiles"))
                .andExpect(content().string(containsString("El precio debe ser mayor que cero")));
        verify(planApi, never()).createPlan(anyString(), any(), anyInt());
    }

    @Test
    void duplicateNameIsReportedNextToTheForm() throws Exception {
        when(planApi.listPlansForAdmin()).thenReturn(List.of(BASIC));
        when(planApi.createPlan(anyString(), any(), anyInt())).thenThrow(new DuplicatePlanNameException("Básico"));

        mockMvc.perform(post("/admin/plans").with(csrf())
                        .param("name", "básico").param("price", "10000").param("maxProfiles", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe un plan con el nombre")));
    }

    @Test
    void renamesAPlan() throws Exception {
        mockMvc.perform(post("/admin/plans/1/rename").with(csrf()).param("name", "  Esencial "))
                .andExpect(redirectedUrl("/admin/plans"))
                .andExpect(flash().attribute("success", containsString("Esencial")));
        verify(planApi).renamePlan(1, "  Esencial ");
    }

    @Test
    void renameErrorsComeBackAsFlashError() throws Exception {
        doThrow(new DuplicatePlanNameException("Premium")).when(planApi).renamePlan(1, "Premium");

        mockMvc.perform(post("/admin/plans/1/rename").with(csrf()).param("name", "Premium"))
                .andExpect(redirectedUrl("/admin/plans"))
                .andExpect(flash().attribute("error", containsString("Premium")));
    }

    @Test
    void deactivatesAndReactivatesAPlan() throws Exception {
        mockMvc.perform(post("/admin/plans/1/deactivate").with(csrf()))
                .andExpect(redirectedUrl("/admin/plans"))
                .andExpect(flash().attributeExists("success"));
        verify(planApi).setPlanActive(1, false);

        mockMvc.perform(post("/admin/plans/9/activate").with(csrf()))
                .andExpect(redirectedUrl("/admin/plans"));
        verify(planApi).setPlanActive(9, true);
    }

    @Test
    void unknownPlanIsReportedAsFlashError() throws Exception {
        doThrow(new PlanNotFoundException(77)).when(planApi).setPlanActive(77, false);

        mockMvc.perform(post("/admin/plans/77/deactivate").with(csrf()))
                .andExpect(redirectedUrl("/admin/plans"))
                .andExpect(flash().attribute("error", containsString("77")));
    }

    @Test
    void changesRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/plans/1/deactivate"))
                .andExpect(status().isForbidden());
        verify(planApi, never()).setPlanActive(anyInt(), anyBoolean());
    }
}
