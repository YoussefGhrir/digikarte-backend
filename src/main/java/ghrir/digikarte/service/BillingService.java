package ghrir.digikarte.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BillingService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.price.monthly}")
    private String monthlyPriceId;

    @Value("${stripe.price.semiannual}")
    private String semiannualPriceId;

    @Value("${stripe.price.yearly}")
    private String yearlyPriceId;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException(
                    "Stripe secret key is missing. Set `STRIPE_SECRET_KEY` (value starts with `sk_...`) " +
                            "in your environment/Config Vars. " +
                            "If you only set a `pk_...` publishable key, that's for the frontend and won't work here."
            );
        }
        // Guard against accidentally using a publishable key (pk_) instead of a secret key (sk_/rk_)
        if (!(stripeSecretKey.startsWith("sk_") || stripeSecretKey.startsWith("rk_"))) {
            throw new IllegalStateException(
                    "Stripe secret key format is invalid. Expected key to start with `sk_` (or `rk_` for restricted keys)."
            );
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException(
                    "Stripe webhook secret is missing. Set `STRIPE_WEBHOOK_SECRET` (value starts with `whsec_...`)."
            );
        }
        if (monthlyPriceId == null || monthlyPriceId.isBlank()
                || semiannualPriceId == null || semiannualPriceId.isBlank()
                || yearlyPriceId == null || yearlyPriceId.isBlank()) {
            throw new IllegalStateException(
                    "One or more Stripe price ids are missing. Expected `stripe.price.monthly`, `stripe.price.semiannual`, `stripe.price.yearly`."
            );
        }
        Stripe.apiKey = stripeSecretKey;
    }

    public String createCheckoutSession(
            String plan,
            String customerEmail,
            Long userId,
            String locale,
            String existingCustomerId,
            boolean hasExistingSubscription
    ) throws StripeException {
        String priceId = switch (plan) {
            case "MONTHLY" -> monthlyPriceId;
            case "SEMIANNUAL" -> semiannualPriceId;
            case "YEARLY" -> yearlyPriceId;
            default -> throw new IllegalArgumentException("Plan inconnu: " + plan);
        };

        SessionCreateParams.Locale stripeLocale =
                "de".equalsIgnoreCase(locale) ? SessionCreateParams.Locale.DE :
                "en".equalsIgnoreCase(locale) ? SessionCreateParams.Locale.EN :
                SessionCreateParams.Locale.FR;

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl("http://localhost:3000/dashboard/subscription?success=1")
                .setCancelUrl("http://localhost:3000/dashboard/subscription?canceled=1")
                .setClientReferenceId(String.valueOf(userId))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                )
                .setLocale(stripeLocale);

        // Si nous avons déjà un client Stripe pour cet utilisateur, on le réutilise.
        // Sinon, on laisse Stripe créer un nouveau client à partir de l'email.
        if (existingCustomerId != null && !existingCustomerId.isBlank()) {
            builder.setCustomer(existingCustomerId);
        } else {
            builder.setCustomerEmail(customerEmail);
        }

        // On ne donne 3 jours d'essai que si l'utilisateur n'a encore aucun abonnement.
        if (!hasExistingSubscription) {
            builder.setSubscriptionData(
                    SessionCreateParams.SubscriptionData.builder()
                            .setTrialPeriodDays(3L)
                            .build()
            );
        }

        SessionCreateParams params = builder.build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    public Event constructEventFromWebhook(String payload, String sigHeader) throws Exception {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    public String createBillingPortalSession(String customerId, String returnUrl, String locale) throws StripeException {
        com.stripe.param.billingportal.SessionCreateParams.Locale portalLocale =
                "de".equalsIgnoreCase(locale) ? com.stripe.param.billingportal.SessionCreateParams.Locale.DE :
                "en".equalsIgnoreCase(locale) ? com.stripe.param.billingportal.SessionCreateParams.Locale.EN :
                com.stripe.param.billingportal.SessionCreateParams.Locale.FR;

        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(returnUrl)
                        .setLocale(portalLocale)
                        .build();
        com.stripe.model.billingportal.Session session =
                com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    public List<Invoice> listInvoicesForCustomer(String customerId) throws StripeException {
        return listInvoicesForCustomer(customerId, 10L);
    }

    public List<Invoice> listInvoicesForCustomer(String customerId, long limit) throws StripeException {
        InvoiceListParams params = InvoiceListParams.builder()
                .setCustomer(customerId)
                .setLimit(limit)
                .build();
        return Invoice.list(params).getData();
    }

    /**
     * Annule immédiatement un abonnement Stripe (utilisé pour stopper un essai ou un abonnement).
     */
    public Subscription cancelSubscriptionNow(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        return subscription.cancel();
    }

    /**
     * Raccourcit la période d'essai en la faisant terminer "maintenant".
     * Stripe passe alors à la période payante du plan choisi.
     */
    public Subscription endTrialNow(String subscriptionId) throws StripeException {
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setTrialEnd(SubscriptionUpdateParams.TrialEnd.NOW)
                .build();
        return Subscription.retrieve(subscriptionId).update(params);
    }

    /**
     * Marque l'abonnement comme "annulé à la fin de la période" (cancel_at_period_end=true).
     * L'utilisateur garde l'accès jusqu'à la fin de la période déjà payée.
     */
    public Subscription cancelAtPeriodEnd(String subscriptionId) throws StripeException {
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        return Subscription.retrieve(subscriptionId).update(params);
    }

    /**
     * Réactive le renouvellement automatique d'un abonnement encore actif
     * (cancel_at_period_end=false). Ne fonctionne que tant que la période n'est pas terminée.
     */
    public Subscription reactivateAutoRenew(String subscriptionId) throws StripeException {
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(false)
                .build();
        return Subscription.retrieve(subscriptionId).update(params);
    }

    /**
     * Détermine le plan métier ("MONTHLY" | "SEMIANNUAL" | "YEARLY") à partir d'un priceId Stripe.
     */
    public String resolvePlanFromPrice(String priceId) {
        if (priceId == null) return "MONTHLY";
        if (priceId.equals(monthlyPriceId)) return "MONTHLY";
        if (priceId.equals(semiannualPriceId)) return "SEMIANNUAL";
        if (priceId.equals(yearlyPriceId)) return "YEARLY";
        return "MONTHLY";
    }
}

