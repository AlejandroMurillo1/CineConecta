package com.cineconecta.platform.billing;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.billing.internal.exceptions.PaymentRejectedException;
import com.cineconecta.platform.billing.internal.service.BillingQueryService;
import com.cineconecta.platform.billing.internal.service.CheckoutService;
import com.cineconecta.platform.billing.internal.service.PaymentSimulationMode;
import com.cineconecta.platform.billing.internal.service.PurchaseResult;
import com.cineconecta.platform.billing.internal.web.BillingController;
import com.cineconecta.platform.shared.CurrentAccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BillingController.class)
@Import({com.cineconecta.platform.shared.config.SharedConfig.class,
        com.cineconecta.platform.shared.PermitAllSecurityTestConfig.class})
class BillingWebMvcTest {

    private static final Instant NOW = Instant.parse("2026-11-25T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingQueryService queryService;
    @MockitoBean
    private CheckoutService checkoutService;
    @MockitoBean
    private ProfileApi profileApi;
    @MockitoBean
    private CurrentAccountProvider currentAccountProvider;
    @MockitoBean
    private Clock clock;

    @Test
    void rendersActivePlansWithProfileWarningWhenTooManyProfiles() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(currentAccountProvider.currentAccountId()).thenReturn(OptionalInt.of(7));
        when(profileApi.countActiveProfiles(7)).thenReturn(3);
        when(queryService.current(7, NOW)).thenReturn(Optional.empty());
        when(queryService.findActivePlans()).thenReturn(List.of(
                new PlanSummaryView(1, "Básico", new BigDecimal("16900.00"), 1, true),
                new PlanSummaryView(2, "Estándar", new BigDecimal("26900.00"), 3, true)
        ));

        mockMvc.perform(get("/billing/plans"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/plans"))
                .andExpect(content().string(containsString("Básico")))
                .andExpect(content().string(containsString("Debes reducir tus perfiles activos")));
    }

    @Test
    void processesApprovedAndRejectedSimulatedCheckoutWithCsrf() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(currentAccountProvider.currentAccountId()).thenReturn(OptionalInt.of(7));
        when(profileApi.countActiveProfiles(7)).thenReturn(1);
        PlanSummaryView basic = new PlanSummaryView(1, "Básico", new BigDecimal("16900.00"), 1, true);
        when(queryService.findActivePlanById(1)).thenReturn(Optional.of(basic));

        PurchaseResult approved = new PurchaseResult(
                new SubscriptionView(55, 7, 1, "Básico", 1, NOW, NOW.plus(Duration.ofHours(720))),
                88,
                new BigDecimal("16900.00"),
                NOW,
                "SIM-1-ABCDEF"
        );
        when(checkoutService.purchaseSubscription(7, 1, PaymentSimulationMode.APPROVED)).thenReturn(approved);

        mockMvc.perform(post("/billing/checkout")
                        .with(csrf())
                        .param("planId", "1")
                        .param("paymentMode", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Suscripción activada con éxito")))
                .andExpect(content().string(containsString("SIM-1-ABCDEF")));

        when(checkoutService.purchaseSubscription(7, 1, PaymentSimulationMode.REJECTED))
                .thenThrow(new PaymentRejectedException("fondos insuficientes"));

        mockMvc.perform(post("/billing/checkout")
                        .with(csrf())
                        .header("HX-Request", "true")
                        .param("planId", "1")
                        .param("paymentMode", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/fragments/checkout-feedback :: feedback"))
                .andExpect(content().string(containsString("Pago rechazado")));
    }

    @Test
    void rendersHistoryPageForAuthenticatedAccount() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(currentAccountProvider.currentAccountId()).thenReturn(OptionalInt.of(7));
        when(queryService.historyForAccount(7)).thenReturn(List.of());
        when(queryService.current(7, NOW)).thenReturn(Optional.empty());

        mockMvc.perform(get("/billing/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/history"))
                .andExpect(content().string(containsString("Historial de suscripciones y pagos")));
    }
}
