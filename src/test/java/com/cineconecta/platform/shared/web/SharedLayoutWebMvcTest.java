package com.cineconecta.platform.shared.web;

import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.shared.config.SharedConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HomeController.class, SharedLayoutWebMvcTest.LayoutProbeController.class})
@Import({SharedConfig.class, SharedLayoutWebMvcTest.LayoutProbeController.class})
class SharedLayoutWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeRendersSharedHeadWithCsrfAndHtmx() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<title>Inicio — CineConecta</title>"),
                        containsString("<meta name=\"_csrf\""),
                        containsString("<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\">"),
                        containsString("/css/app.css"),
                        containsString("/vendor/htmx/htmx-2.0.4.min.js"),
                        containsString("/js/app.js"),
                        containsString("class=\"cc-footer\""))));
    }

    @Test
    void anonymousNavbarOffersLoginAndRegister() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(content().string(allOf(
                        containsString("href=\"/login\""),
                        containsString("href=\"/register\""),
                        not(containsString("Cerrar sesión")),
                        not(containsString("Historial y pagos")))));
    }

    @Test
    void authenticatedNavbarShowsProfileAndLogout() throws Exception {
        mockMvc.perform(get("/test/layout").param("withProfile", "true").with(user("42")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Cerrar sesión"),
                        containsString("href=\"/viewing/profiles/7/my-list\""),
                        containsString(">Sofía</a>"),
                        containsString("href=\"/billing/plans\" class=\"active\""),
                        not(containsString("href=\"/register\"")))));
    }

    @Test
    void moduleStylesheetsAreAddedAfterAppCss() throws Exception {
        String html = mockMvc.perform(get("/test/layout"))
                .andReturn().getResponse().getContentAsString();

        int app = html.indexOf("/css/app.css");
        int billing = html.indexOf("/css/billing.css");
        org.assertj.core.api.Assertions.assertThat(app).isPositive();
        org.assertj.core.api.Assertions.assertThat(billing).isGreaterThan(app);
    }

    @Test
    void flashMessagesAreRenderedEscaped() throws Exception {
        mockMvc.perform(get("/test/layout").param("error", "<script>x</script>"))
                .andExpect(content().string(allOf(
                        containsString("cc-alert-danger"),
                        containsString("&lt;script&gt;x&lt;/script&gt;"),
                        not(containsString("<script>x</script>")))));
    }

    @Test
    void errorPageShowsFriendlyTextWithoutInternals() throws Exception {
        mockMvc.perform(get("/test/error").param("code", "404"))
                .andExpect(content().string(allOf(
                        containsString("Página no encontrada"),
                        not(containsString("NullPointerException")))));

        mockMvc.perform(get("/test/error").param("code", "503"))
                .andExpect(content().string(containsString("Servicio no disponible")));

        mockMvc.perform(get("/test/error").param("code", "500"))
                .andExpect(content().string(containsString("Algo salió mal")));
    }

    @Controller
    static class LayoutProbeController {

        @GetMapping("/test/layout")
        String layout(@RequestParam(defaultValue = "false") boolean withProfile,
                      @RequestParam(required = false) String error, Model model) {
            if (withProfile) {
                model.addAttribute("currentProfile", new ProfileView(7, 42, "Sofía", 13, true));
            }
            model.addAttribute("error", error);
            return "shared-test/with-extras";
        }

        @GetMapping("/test/error")
        String error(@RequestParam int code, Model model) {
            model.addAttribute("status", code);
            model.addAttribute("trace", "java.lang.NullPointerException");
            return "error";
        }
    }
}
