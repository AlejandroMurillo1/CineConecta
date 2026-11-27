package com.cineconecta.platform.billing.internal.web;

import com.cineconecta.platform.accounts.ProfileApi;
import com.cineconecta.platform.billing.PlanSummaryView;
import com.cineconecta.platform.billing.SubscriptionView;
import com.cineconecta.platform.billing.internal.exceptions.BillingException;
import com.cineconecta.platform.billing.internal.service.BillingQueryService;
import com.cineconecta.platform.billing.internal.service.CheckoutService;
import com.cineconecta.platform.billing.internal.service.PaymentSimulationMode;
import com.cineconecta.platform.billing.internal.service.PurchaseResult;
import com.cineconecta.platform.billing.internal.service.SubscriptionHistoryItem;
import com.cineconecta.platform.shared.CurrentAccountProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

@Controller
@RequestMapping("/billing")
public class BillingController {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BOGOTA_ZONE);

    private final BillingQueryService queryService;
    private final CheckoutService checkoutService;
    private final ProfileApi profileApi;
    private final CurrentAccountProvider currentAccountProvider;
    private final Clock clock;

    public BillingController(
            BillingQueryService queryService,
            CheckoutService checkoutService,
            ProfileApi profileApi,
            CurrentAccountProvider currentAccountProvider,
            Clock clock) {
        this.queryService = queryService;
        this.checkoutService = checkoutService;
        this.profileApi = profileApi;
        this.currentAccountProvider = currentAccountProvider;
        this.clock = clock;
    }

    @GetMapping("/plans")
    public String listPlans(Model model) {
        List<PlanSummaryView> plans = queryService.findActivePlans();
        OptionalInt accountOpt = currentAccountProvider.currentAccountId();
        Instant now = clock.instant();

        Optional<SubscriptionView> currentSub = accountOpt.isPresent()
                ? queryService.current(accountOpt.getAsInt(), now)
                : Optional.empty();
        int activeProfiles = accountOpt.isPresent()
                ? profileApi.countActiveProfiles(accountOpt.getAsInt())
                : 0;

        model.addAttribute("plans", plans);
        model.addAttribute("authenticated", accountOpt.isPresent());
        model.addAttribute("currentSubscription", currentSub.orElse(null));
        model.addAttribute("activeProfiles", activeProfiles);
        model.addAttribute("fmt", DATE_TIME_FMT);
        return "billing/plans";
    }

    @GetMapping("/checkout/{planId}")
    public String checkoutPage(@PathVariable int planId, Model model) {
        OptionalInt accountOpt = currentAccountProvider.currentAccountId();
        Optional<PlanSummaryView> planOpt = queryService.findActivePlanById(planId);
        Instant now = clock.instant();

        model.addAttribute("plan", planOpt.orElse(null));
        model.addAttribute("previewStart", DATE_TIME_FMT.format(now));
        model.addAttribute("previewEnd", DATE_TIME_FMT.format(now.plus(CheckoutService.SUBSCRIPTION_DURATION)));
        model.addAttribute("fmt", DATE_TIME_FMT);

        if (accountOpt.isEmpty()) {
            model.addAttribute("authenticated", false);
            model.addAttribute("errorMessage", "Debes iniciar sesión con tu cuenta para contratar un plan.");
            return "billing/checkout";
        }

        int accountId = accountOpt.getAsInt();
        model.addAttribute("authenticated", true);
        model.addAttribute("activeProfiles", profileApi.countActiveProfiles(accountId));
        model.addAttribute("currentSubscription", queryService.current(accountId, now).orElse(null));

        if (planOpt.isEmpty()) {
            model.addAttribute("errorMessage", "El plan solicitado no existe o no está disponible.");
        }
        return "billing/checkout";
    }

    @PostMapping("/checkout")
    public String submitCheckout(
            @RequestParam int planId,
            @RequestParam(defaultValue = "APPROVED") PaymentSimulationMode paymentMode,
            @RequestHeader(value = "HX-Request", defaultValue = "false") boolean htmxRequest,
            Model model) {

        OptionalInt accountOpt = currentAccountProvider.currentAccountId();
        Instant now = clock.instant();
        model.addAttribute("fmt", DATE_TIME_FMT);
        model.addAttribute("plan", queryService.findActivePlanById(planId).orElse(null));
        model.addAttribute("previewStart", DATE_TIME_FMT.format(now));
        model.addAttribute("previewEnd", DATE_TIME_FMT.format(now.plus(CheckoutService.SUBSCRIPTION_DURATION)));

        if (accountOpt.isEmpty()) {
            model.addAttribute("authenticated", false);
            model.addAttribute("errorMessage", "Debes iniciar sesión con tu cuenta para completar la contratación.");
            return htmxRequest ? "billing/fragments/checkout-feedback :: feedback" : "billing/checkout";
        }

        int accountId = accountOpt.getAsInt();
        model.addAttribute("authenticated", true);
        model.addAttribute("activeProfiles", profileApi.countActiveProfiles(accountId));

        try {
            PurchaseResult result = checkoutService.purchaseSubscription(accountId, planId, paymentMode);
            model.addAttribute("purchaseResult", result);
            model.addAttribute("currentSubscription", result.subscription());
        } catch (BillingException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("currentSubscription", queryService.current(accountId, now).orElse(null));
        }

        return htmxRequest ? "billing/fragments/checkout-feedback :: feedback" : "billing/checkout";
    }

    @GetMapping("/history")
    public String history(Model model) {
        OptionalInt accountOpt = currentAccountProvider.currentAccountId();
        model.addAttribute("fmt", DATE_TIME_FMT);

        if (accountOpt.isEmpty()) {
            model.addAttribute("authenticated", false);
            model.addAttribute("history", List.<SubscriptionHistoryItem>of());
            model.addAttribute("errorMessage", "Debes iniciar sesión para consultar tu historial de suscripciones y pagos.");
            return "billing/history";
        }

        int accountId = accountOpt.getAsInt();
        List<SubscriptionHistoryItem> items = queryService.historyForAccount(accountId);
        model.addAttribute("authenticated", true);
        model.addAttribute("history", items);
        model.addAttribute("currentSubscription", queryService.current(accountId, clock.instant()).orElse(null));
        return "billing/history";
    }
}
