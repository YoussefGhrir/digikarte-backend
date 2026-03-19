package ghrir.digikarte.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import ghrir.digikarte.dto.InvoiceDto;
import ghrir.digikarte.dto.SubscriptionDto;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.UserRepository;
import ghrir.digikarte.security.JwtService;
import ghrir.digikarte.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingService billingService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    private User getCurrentUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Non authentifié");
        }
        String token = authHeader.substring(7);
        String email = jwtService.extractEmail(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    private static String toIso(long epochSeconds) {
        return DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(epochSeconds));
    }

    private static String normalizeStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return "CANCELLED";
        }
        String upper = stripeStatus.toUpperCase();
        // Stripe renvoie "CANCELED" (une seule L) → on normalise vers "CANCELLED"
        if ("CANCELED".equals(upper)) {
            return "CANCELLED";
        }
        // Certains statuts Stripe (INCOMPLETE, PAST_DUE, UNPAID) peuvent être vus
        // comme "EXPIRED" côté produit pour simplifier.
        if ("INCOMPLETE".equals(upper) || "PAST_DUE".equals(upper) || "UNPAID".equals(upper)) {
            return "EXPIRED";
        }
        return upper;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@RequestBody Map<String, String> body,
                                            HttpServletRequest request) {
        String plan = body.get("plan");
        String locale = body.getOrDefault("locale", "fr");
        User user = getCurrentUser(request);

        boolean hasExistingSub = user.getStripeSubscriptionId() != null && !user.getStripeSubscriptionId().isBlank();
        String existingCustomerId = user.getStripeCustomerId();

        try {
            String url = billingService.createCheckoutSession(
                    plan,
                    user.getEmail(),
                    user.getId(),
                    locale,
                    existingCustomerId,
                    hasExistingSub
            );
            return ResponseEntity.ok(Map.of("checkoutUrl", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage()
            ));
        } catch (StripeException e) {
            // Cas fréquent en test : le customer Stripe a été supprimé manuellement.
            if ("resource_missing".equals(e.getCode())
                    && e.getMessage() != null
                    && e.getMessage().contains("No such customer")
                    && existingCustomerId != null && !existingCustomerId.isBlank()) {
                try {
                    // On oublie l'ancien customer côté base et on recrée une session sans le réutiliser.
                    user.setStripeCustomerId(null);
                    userRepository.save(user);

                    String url = billingService.createCheckoutSession(
                            plan,
                            user.getEmail(),
                            user.getId(),
                            locale,
                            null, // pas de customer à réutiliser
                            hasExistingSub
                    );
                    return ResponseEntity.ok(Map.of("checkoutUrl", url));
                } catch (StripeException ex) {
                    String detail2 = ex.getMessage() != null ? ex.getMessage() : "Erreur Stripe lors de la création de la session de paiement.";
                    return ResponseEntity.status(502).body(Map.of(
                            "message", detail2,
                            "code", "STRIPE_CHECKOUT_ERROR"
                    ));
                }
            }

            String detail = e.getMessage() != null ? e.getMessage() : "Erreur Stripe lors de la création de la session de paiement.";
            return ResponseEntity.status(502).body(Map.of(
                    "message", detail,
                    "code", "STRIPE_CHECKOUT_ERROR"
            ));
        }
    }

    @PostMapping("/me/payment-portal")
    public ResponseEntity<Map<String, String>> openPaymentPortal(@RequestBody Map<String, String> body,
                                                                 HttpServletRequest request) throws StripeException {
        User user = getCurrentUser(request);
        String customerId = user.getStripeCustomerId();
        String locale = body.getOrDefault("locale", "fr");

        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String portalUrl = billingService.createBillingPortalSession(
                customerId,
                "http://localhost:3000/dashboard/subscription",
                locale
        );

        return ResponseEntity.ok(Map.of("url", portalUrl));
    }

    @GetMapping("/me/subscription")
    public ResponseEntity<SubscriptionDto> getMySubscription(HttpServletRequest request) throws StripeException {
        User user = getCurrentUser(request);
        String stripeSubId = user.getStripeSubscriptionId();

        if (stripeSubId == null || stripeSubId.isBlank()) {
            // Pas d'abonnement enregistré pour cet utilisateur → 204 No Content
            return ResponseEntity.noContent().build();
        }

        try {
            Subscription s = billingService.retrieveSubscription(stripeSubId);

            String status = normalizeStatus(s.getStatus());
            boolean autoRenew = !Boolean.TRUE.equals(s.getCancelAtPeriodEnd());

            String priceId = s.getItems().getData().get(0).getPrice().getId();
            String planName = billingService.resolvePlanFromPrice(priceId);

            long amount = s.getItems().getData().get(0).getPrice().getUnitAmount();
            String currency = s.getItems().getData().get(0).getPrice().getCurrency().toUpperCase();

            SubscriptionDto dto = SubscriptionDto.builder()
                    .plan(planName)
                    .status(status)
                    .trialEnd(s.getTrialEnd() != null ? toIso(s.getTrialEnd()) : null)
                    .currentPeriodStart(s.getCurrentPeriodStart() != null ? toIso(s.getCurrentPeriodStart()) : null)
                    .currentPeriodEnd(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                    .nextPaymentAt(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                    .autoRenew(autoRenew)
                    .currency(currency)
                    .amount(amount / 100.0)
                    .build();

            return ResponseEntity.ok(dto);
        } catch (IllegalStateException e) {
            // Stripe pas configuré côté backend => désactiver proprement la vue abonnement
            log.error("Billing unavailable: {}", e.getMessage());
            return ResponseEntity.status(503).build();
        } catch (StripeException e) {
            // Si la subscription Stripe n'existe plus (ou IDs incohérents), on nettoie en DB
            String code = e.getCode();
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if ("resource_missing".equals(code) && msg.toLowerCase().contains("no such subscription")) {
                user.setStripeSubscriptionId(null);
                // le customer peut encore être valide, on le garde
                userRepository.save(user);
                return ResponseEntity.noContent().build();
            }

            log.error("Stripe error while retrieving subscription. code={}, msg={}", code, msg);
            return ResponseEntity.status(502).build();
        }
    }

    @PostMapping("/me/subscription/cancel")
    public ResponseEntity<Void> cancelMySubscription(HttpServletRequest request) throws StripeException {
        User user = getCurrentUser(request);
        String stripeSubId = user.getStripeSubscriptionId();
        if (stripeSubId == null || stripeSubId.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        billingService.cancelSubscriptionNow(stripeSubId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/subscription/skip-trial")
    public ResponseEntity<SubscriptionDto> skipTrialAndActivate(HttpServletRequest request) throws StripeException {
        User user = getCurrentUser(request);
        String stripeSubId = user.getStripeSubscriptionId();
        if (stripeSubId == null || stripeSubId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Subscription s = billingService.endTrialNow(stripeSubId);

        String status = normalizeStatus(s.getStatus());
        boolean autoRenew = !Boolean.TRUE.equals(s.getCancelAtPeriodEnd());

        String priceId = s.getItems().getData().get(0).getPrice().getId();
        String planName = billingService.resolvePlanFromPrice(priceId);
        long amount = s.getItems().getData().get(0).getPrice().getUnitAmount();
        String currency = s.getItems().getData().get(0).getPrice().getCurrency().toUpperCase();

        SubscriptionDto dto = SubscriptionDto.builder()
                .plan(planName)
                .status(status)
                .trialEnd(s.getTrialEnd() != null ? toIso(s.getTrialEnd()) : null)
                .currentPeriodStart(s.getCurrentPeriodStart() != null ? toIso(s.getCurrentPeriodStart()) : null)
                .currentPeriodEnd(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                .nextPaymentAt(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                .autoRenew(autoRenew)
                .currency(currency)
                .amount(amount / 100.0)
                .build();

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/me/subscription/cancel-at-period-end")
    public ResponseEntity<SubscriptionDto> cancelAtPeriodEnd(HttpServletRequest request) throws StripeException {
        User user = getCurrentUser(request);
        String stripeSubId = user.getStripeSubscriptionId();
        if (stripeSubId == null || stripeSubId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Subscription s = billingService.cancelAtPeriodEnd(stripeSubId);

        String status = normalizeStatus(s.getStatus());
        boolean autoRenew = !Boolean.TRUE.equals(s.getCancelAtPeriodEnd());

        String priceId = s.getItems().getData().get(0).getPrice().getId();
        String planName = billingService.resolvePlanFromPrice(priceId);
        long amount = s.getItems().getData().get(0).getPrice().getUnitAmount();
        String currency = s.getItems().getData().get(0).getPrice().getCurrency().toUpperCase();

        SubscriptionDto dto = SubscriptionDto.builder()
                .plan(planName)
                .status(status)
                .trialEnd(s.getTrialEnd() != null ? toIso(s.getTrialEnd()) : null)
                .currentPeriodStart(s.getCurrentPeriodStart() != null ? toIso(s.getCurrentPeriodStart()) : null)
                .currentPeriodEnd(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                .nextPaymentAt(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                .autoRenew(autoRenew)
                .currency(currency)
                .amount(amount / 100.0)
                .build();

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/me/subscription/reactivate")
    public ResponseEntity<SubscriptionDto> reactivateAutoRenew(HttpServletRequest request) throws StripeException {
        User user = getCurrentUser(request);
        String stripeSubId = user.getStripeSubscriptionId();
        if (stripeSubId == null || stripeSubId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Subscription s = billingService.reactivateAutoRenew(stripeSubId);

        String status = normalizeStatus(s.getStatus());
        boolean autoRenew = !Boolean.TRUE.equals(s.getCancelAtPeriodEnd());

        String priceId = s.getItems().getData().get(0).getPrice().getId();
        String planName = billingService.resolvePlanFromPrice(priceId);
        long amount = s.getItems().getData().get(0).getPrice().getUnitAmount();
        String currency = s.getItems().getData().get(0).getPrice().getCurrency().toUpperCase();

        SubscriptionDto dto = SubscriptionDto.builder()
                .plan(planName)
                .status(status)
                .trialEnd(s.getTrialEnd() != null ? toIso(s.getTrialEnd()) : null)
                .currentPeriodStart(s.getCurrentPeriodStart() != null ? toIso(s.getCurrentPeriodStart()) : null)
                .currentPeriodEnd(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                .nextPaymentAt(s.getCurrentPeriodEnd() != null ? toIso(s.getCurrentPeriodEnd()) : null)
                .autoRenew(autoRenew)
                .currency(currency)
                .amount(amount / 100.0)
                .build();

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/me/invoices")
    public ResponseEntity<List<InvoiceDto>> getMyInvoices(HttpServletRequest request) throws StripeException {
        User user = getCurrentUser(request);
        String customerId = user.getStripeCustomerId();

        if (customerId == null || customerId.isBlank()) {
            // Pas de client Stripe associé → 204 No Content
            return ResponseEntity.noContent().build();
        }

        List<Invoice> invoices;
        try {
            invoices = billingService.listInvoicesForCustomer(customerId);
        } catch (IllegalStateException e) {
            log.error("Billing unavailable: {}", e.getMessage());
            return ResponseEntity.status(503).build();
        } catch (StripeException e) {
            String code = e.getCode();
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if ("resource_missing".equals(code) && msg.toLowerCase().contains("no such customer")) {
                // IDs client incohérents => on nettoie et on évite 500
                user.setStripeCustomerId(null);
                user.setStripeSubscriptionId(null);
                userRepository.save(user);
                return ResponseEntity.noContent().build();
            }

            log.error("Stripe error while retrieving invoices. code={}, msg={}", code, msg);
            return ResponseEntity.status(502).build();
        }

        List<InvoiceDto> dtoList = invoices.stream().map(inv -> {
            Long createdEpoch = inv.getCreated();
            // Utiliser le montant payé si disponible, sinon le montant dû
            Long amountPaid = inv.getAmountPaid();
            Long amountDue = inv.getAmountDue();
            long rawAmount = amountPaid != null ? amountPaid : (amountDue != null ? amountDue : 0L);
            double amount = rawAmount / 100.0;

            Long paidAtEpoch = inv.getStatusTransitions() != null
                    ? inv.getStatusTransitions().getPaidAt()
                    : null;

            return InvoiceDto.builder()
                    .id(inv.getId())
                    .amount(amount)
                    .currency(inv.getCurrency() != null ? inv.getCurrency().toUpperCase() : "EUR")
                    .status(inv.getStatus() != null ? inv.getStatus().toUpperCase() : "PENDING")
                    .createdAt(createdEpoch != null ? toIso(createdEpoch) : null)
                    .paidAt(paidAtEpoch != null ? toIso(paidAtEpoch) : null)
                    .invoiceUrl(inv.getInvoicePdf())
                    .build();
        }).toList();

        return ResponseEntity.ok(dtoList);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload,
                                                @RequestHeader("Stripe-Signature") String sigHeader) throws Exception {
        Event event = billingService.constructEventFromWebhook(payload, sigHeader);
        log.info("Stripe webhook received: type={}", event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject().orElseThrow();
                // Reliage session <-> utilisateur via client_reference_id
                // (dans BillingService on met setClientReferenceId(String.valueOf(userId))).
                String clientRef = session.getClientReferenceId();
                if (clientRef != null && !clientRef.isBlank()) {
                    Long userId = null;
                    String customerId = null;
                    String subscriptionId = null;
                    try {
                        final Long parsedUserId = Long.valueOf(clientRef);
                        userId = parsedUserId;
                        User user = userRepository.findById(parsedUserId)
                                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + parsedUserId));

                        // customer + subscription sont en général présentes sur une session checkout complétée
                        customerId = session.getCustomer() != null ? session.getCustomer().toString() : null;
                        subscriptionId = session.getSubscription() != null ? session.getSubscription().toString() : null;

                        // Fallback: si Stripe ne renvoie pas directement `customer` sur la session,
                        // on récupère le Subscription pour retrouver le customer.
                        if ((customerId == null || customerId.isBlank())
                                && (subscriptionId != null && !subscriptionId.isBlank())) {
                            try {
                                Subscription sub = billingService.retrieveSubscription(subscriptionId);
                                Object subCustomer = sub.getCustomer();
                                customerId = subCustomer != null ? subCustomer.toString() : null;
                            } catch (Exception e) {
                                log.warn(
                                        "Stripe webhook fallback failed to resolve customer from subscription. eventType={}, subscriptionId={}",
                                        event.getType(),
                                        subscriptionId
                                );
                            }
                        }

                        if (customerId != null && !customerId.isBlank()) {
                            user.setStripeCustomerId(customerId);
                        }
                        if (subscriptionId != null && !subscriptionId.isBlank()) {
                            user.setStripeSubscriptionId(subscriptionId);
                        }

                        userRepository.save(user);
                        User reloaded = userRepository.findById(parsedUserId).orElse(user);

                        String persistedCustomerId = reloaded.getStripeCustomerId();
                        String persistedSubscriptionId = reloaded.getStripeSubscriptionId();

                        log.info(
                                "Stripe webhook saved ids (attempt): userId={}, stripeCustomerId={}, stripeSubscriptionId={}",
                                userId,
                                customerId,
                                subscriptionId
                        );
                        log.info(
                                "Stripe webhook saved ids (db): userId={}, stripeCustomerId={}, stripeSubscriptionId={}",
                                parsedUserId,
                                reloaded.getStripeCustomerId(),
                                reloaded.getStripeSubscriptionId()
                        );

                        boolean expectedCustomer = customerId != null && !customerId.isBlank();
                        boolean expectedSubscription = subscriptionId != null && !subscriptionId.isBlank();

                        boolean customerPersistedOk = !expectedCustomer
                                || (persistedCustomerId != null && customerId.equals(persistedCustomerId));
                        boolean subscriptionPersistedOk = !expectedSubscription
                                || (persistedSubscriptionId != null && subscriptionId.equals(persistedSubscriptionId));

                        if (!customerPersistedOk || !subscriptionPersistedOk) {
                            log.error(
                                    "Stripe webhook verification failed (attempted write mismatch). userId={}, expectedCustomerId={}, persistedCustomerId={}, expectedSubscriptionId={}, persistedSubscriptionId={}",
                                    parsedUserId,
                                    customerId,
                                    persistedCustomerId,
                                    subscriptionId,
                                    persistedSubscriptionId
                            );

                            // Retry best-effort: on ré-enregistre les champs attendus si nécessaire.
                            if (expectedCustomer && !customerPersistedOk) {
                                reloaded.setStripeCustomerId(customerId);
                            }
                            if (expectedSubscription && !subscriptionPersistedOk) {
                                reloaded.setStripeSubscriptionId(subscriptionId);
                            }

                            userRepository.save(reloaded);
                            User reloadedAfterRetry = userRepository.findById(parsedUserId).orElse(reloaded);
                            log.info(
                                    "Stripe webhook verification retry (db): userId={}, stripeCustomerId={}, stripeSubscriptionId={}",
                                    parsedUserId,
                                    reloadedAfterRetry.getStripeCustomerId(),
                                    reloadedAfterRetry.getStripeSubscriptionId()
                            );
                        }
                    } catch (Exception ignored) {
                        // Le webhook doit répondre "ok" même si l'enregistrement en base échoue.
                        // On évite de casser tout le flux Stripe.
                        log.error(
                                "Stripe webhook failed to save ids. eventType=checkout.session.completed clientRef={} userId={} customerId={} subscriptionId={}",
                                clientRef,
                                userId,
                                customerId,
                                subscriptionId,
                                ignored
                        );
                    }
                }
            }
            case "customer.subscription.updated", "customer.subscription.created" -> {
                Subscription sub = (Subscription) event.getDataObjectDeserializer()
                        .getObject().orElseThrow();
                log.info("Stripe webhook received subscription event: id={}, status={}", sub.getId(), sub.getStatus());
                // Optionnel: on peut enrichir la base ici si tu stockes plus de champs.
            }
            case "invoice.payment_succeeded", "invoice.payment_failed" -> {
                // TODO: éventuellement créer / mettre à jour des factures en base.
                Invoice inv = (Invoice) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (inv != null) {
                    log.info(
                            "Stripe webhook received invoice event: invoiceId={}, customer={}, status={}",
                            inv.getId(),
                            inv.getCustomer(),
                            inv.getStatus()
                    );
                } else {
                    log.info("Stripe webhook received invoice event: type={}", event.getType());
                }
            }
            default -> {
                // autres événements ignorés
                log.debug("Stripe webhook ignored event type={}", event.getType());
            }
        }

        return ResponseEntity.ok("ok");
    }
}

