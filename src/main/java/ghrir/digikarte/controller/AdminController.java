package ghrir.digikarte.controller;

import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import ghrir.digikarte.dto.admin.*;
import ghrir.digikarte.entity.Organization;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.MenuRepository;
import ghrir.digikarte.repository.OrganizationRepository;
import ghrir.digikarte.repository.UserRepository;
import ghrir.digikarte.service.AdminBootstrapService;
import ghrir.digikarte.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MenuRepository menuRepository;
    private final BillingService billingService;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String email = authentication.getName();
        if (email == null || !email.equalsIgnoreCase(AdminBootstrapService.DEFAULT_ADMIN_EMAIL)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private static String normalizeStatus(String stripeStatus) {
        if (stripeStatus == null) return "CANCELLED";
        String upper = stripeStatus.toUpperCase();
        if ("CANCELED".equals(upper)) return "CANCELLED";
        if ("INCOMPLETE".equals(upper) || "PAST_DUE".equals(upper) || "UNPAID".equals(upper)) return "EXPIRED";
        return upper;
    }

    private record SubscriptionInfo(String status, String plan) {}

    private SubscriptionInfo getSubscriptionInfo(User user) {
        // Bypass admin (VIP): toujours actif côté admin.
        if (user.isSubscriptionBypass()) {
            return new SubscriptionInfo("ACTIVE", "VIP");
        }

        String subId = user.getStripeSubscriptionId();
        if (subId == null || subId.isBlank()) {
            return new SubscriptionInfo("NO_SUBSCRIPTION", null);
        }
        try {
            Subscription s = billingService.retrieveSubscription(subId);
            String status = normalizeStatus(s.getStatus());

            String plan = null;
            try {
                String priceId = s.getItems().getData().get(0).getPrice().getId();
                plan = billingService.resolvePlanFromPrice(priceId);
            } catch (Exception ignored) {
            }

            return new SubscriptionInfo(status, plan);
        } catch (Exception ignored) {
            return new SubscriptionInfo("ERROR", null);
        }
    }

    private String primaryCountryForUser(User user) {
        List<Organization> orgs = organizationRepository.findByOwnerId(user.getId());
        if (orgs == null || orgs.isEmpty()) return null;
        return orgs.stream()
                .map(Organization::getCountry)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(null);
    }

    private long menusCountForUser(User user) {
        List<Organization> orgs = organizationRepository.findByOwnerId(user.getId());
        if (orgs == null || orgs.isEmpty()) return 0L;
        long total = 0;
        for (Organization org : orgs) {
            total += menuRepository.countByOrganizationId(org.getId());
        }
        return total;
    }

    private int organizationsCountForUser(User user) {
        List<Organization> orgs = organizationRepository.findByOwnerId(user.getId());
        return orgs == null ? 0 : orgs.size();
    }

    private String profilePhotoBase64(User user) {
        try {
            byte[] photo = user.getProfilePhoto();
            if (photo == null || photo.length == 0) return null;
            return Base64.getEncoder().encodeToString(photo);
        } catch (Exception ignored) {
            return null;
        }
    }

    @GetMapping("/metrics")
    public AdminMetricsDto metrics(
            @RequestParam(name = "days", required = false, defaultValue = "30") int days,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        if (days < 1) days = 30;
        try {
            List<User> users = userRepository.findAll();
            int totalUsers = users.size();

            Map<Long, String> statusByUserId = new HashMap<>();
            for (User u : users) {
                SubscriptionInfo info = getSubscriptionInfo(u);
                statusByUserId.put(u.getId(), info.status());
            }

            int activeSubs = 0;
            int trialingSubs = 0;
            int expiredSubs = 0;
            int cancelledSubs = 0;

            for (String status : statusByUserId.values()) {
                switch (status) {
                    case "ACTIVE" -> activeSubs++;
                    case "TRIALING" -> trialingSubs++;
                    case "EXPIRED" -> expiredSubs++;
                    case "CANCELLED" -> cancelledSubs++;
                }
            }

            double subscriptionActiveRate = totalUsers > 0 ? (activeSubs + trialingSubs) * 1.0 / totalUsers : 0.0;

            // Revenue (approx.) : somme des 10 dernières invoices payées par client Stripe
            long cutoffEpochSeconds = Instant.now().getEpochSecond() - (days * 86400L);
            double revenuePaid = 0.0;
            String revenueCurrency = null;
            for (User u : users) {
                String customerId = u.getStripeCustomerId();
                if (customerId == null || customerId.isBlank()) continue;
                try {
                    List<Invoice> invoices = billingService.listInvoicesForCustomer(customerId, 10L);
                    for (Invoice inv : invoices) {
                        if (inv.getCreated() == null || inv.getStatus() == null) continue;
                        long created = inv.getCreated();
                        if (created < cutoffEpochSeconds) continue;
                        String invStatus = inv.getStatus().toString().toUpperCase();
                        if (!"PAID".equals(invStatus)) continue;
                        long rawAmount = inv.getAmountPaid() != null ? inv.getAmountPaid() : 0L;
                        revenuePaid += rawAmount / 100.0;
                        if (revenueCurrency == null && inv.getCurrency() != null) {
                            revenueCurrency = inv.getCurrency().toUpperCase();
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // Stats par pays (country d'organization)
            Map<String, Set<Long>> userIdsByCountry = new HashMap<>();
            Map<String, Long> menusCountByCountry = new HashMap<>();
            for (User u : users) {
                List<Organization> orgs = organizationRepository.findByOwnerId(u.getId());
                if (orgs == null || orgs.isEmpty()) {
                    userIdsByCountry.computeIfAbsent("UNKNOWN", k -> new HashSet<>()).add(u.getId());
                    continue;
                }
                for (Organization org : orgs) {
                    String country = org.getCountry();
                    if (country == null || country.isBlank()) country = "UNKNOWN";
                    userIdsByCountry.computeIfAbsent(country, k -> new HashSet<>()).add(u.getId());
                    menusCountByCountry.merge(country, menuRepository.countByOrganizationId(org.getId()), Long::sum);
                }
            }

            List<CountryMetricsDto> byCountry = new ArrayList<>();
            for (Map.Entry<String, Set<Long>> entry : userIdsByCountry.entrySet()) {
                String country = entry.getKey();
                Set<Long> userIds = entry.getValue();

                int usersCount = userIds.size();
                int cActive = 0;
                int cTrialing = 0;
                int cExpired = 0;
                int cCancelled = 0;
                for (Long uid : userIds) {
                    String st = statusByUserId.get(uid);
                    if ("ACTIVE".equals(st)) cActive++;
                    else if ("TRIALING".equals(st)) cTrialing++;
                    else if ("EXPIRED".equals(st)) cExpired++;
                    else if ("CANCELLED".equals(st)) cCancelled++;
                }

                double rate = usersCount > 0 ? (cActive + cTrialing) * 1.0 / usersCount : 0.0;
                long menusCount = menusCountByCountry.getOrDefault(country, 0L);
                byCountry.add(new CountryMetricsDto(country, usersCount, menusCount, cActive, cTrialing, cExpired, cCancelled, rate, null));
            }

            // Tri : par taux d'abonnement puis par nombre de menus
            byCountry.sort((a, b) -> {
                int cmp = Double.compare(b.getSubscriptionRate(), a.getSubscriptionRate());
                if (cmp != 0) return cmp;
                return Long.compare(b.getMenusCount(), a.getMenusCount());
            });

            if (revenueCurrency == null) revenueCurrency = "EUR";

            return new AdminMetricsDto(totalUsers, activeSubs, trialingSubs, expiredSubs, cancelledSubs, subscriptionActiveRate, revenuePaid, revenueCurrency, byCountry);
        } catch (Exception ignored) {
            // Ne jamais casser l'écran admin.
            return new AdminMetricsDto(0, 0, 0, 0, 0, 0.0, 0.0, "EUR", new ArrayList<>());
        }
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers(
            @RequestParam(name = "q", required = false) String q,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        try {
            List<User> users = userRepository.findAll();
            // On exclut le compte owner/admin du tableau.
            // (C'est l'utilisateur connecté qui doit rester "lui-même" dans la sidebar, pas dans la gestion users.)
            users = users.stream()
                    .filter(u -> u.getEmail() == null || !u.getEmail().equalsIgnoreCase(AdminBootstrapService.DEFAULT_ADMIN_EMAIL))
                    .toList();

            String query = q != null ? q.trim().toLowerCase() : null;
            if (query != null && !query.isBlank()) {
                users = users.stream().filter(u ->
                                (u.getEmail() != null && u.getEmail().toLowerCase().contains(query)) ||
                                (u.getNom() != null && u.getNom().toLowerCase().contains(query)) ||
                                (u.getPrenom() != null && u.getPrenom().toLowerCase().contains(query)) ||
                                (u.getTelephone() != null && u.getTelephone().toLowerCase().contains(query))
                        )
                        .toList();
            }

            List<AdminUserDto> out = new ArrayList<>();
            for (User u : users) {
                SubscriptionInfo sub;
                try {
                    sub = getSubscriptionInfo(u);
                } catch (Exception ignored) {
                    sub = new SubscriptionInfo("ERROR", null);
                }

                String country = null;
                int orgCount = 0;
                long menusCount = 0L;
                try {
                    country = primaryCountryForUser(u);
                    orgCount = organizationsCountForUser(u);
                    menusCount = menusCountForUser(u);
                } catch (Exception ignored) {
                    // Best-effort: on affiche au moins l'utilisateur même si certaines stats échouent.
                }

                String photo = profilePhotoBase64(u);

                out.add(new AdminUserDto(
                        u.getId(),
                        u.getNom(),
                        u.getPrenom(),
                        u.getEmail(),
                        u.getTelephone(),
                        country,
                        orgCount,
                        menusCount,
                        sub.status(),
                        sub.plan(),
                        u.isSubscriptionBypass(),
                        photo
                ));
            }
            return out;
        } catch (Exception e) {
            // On ne veut jamais casser l'écran admin.
            return new ArrayList<>();
        }
    }

    @PostMapping("/users")
    public AdminUserDto createUser(
            @RequestBody AdminCreateUserRequest request,
            Authentication authentication
    ) {
        requireAdmin(authentication);

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email required");
        }
        if (request.getNom() == null || request.getNom().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom required");
        }
        if (request.getPrenom() == null || request.getPrenom().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prenom required");
        }
        if (request.getTelephone() == null || request.getTelephone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telephone required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password required");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
        }

        boolean bypass = request.getSubscriptionBypass() == null || request.getSubscriptionBypass();

        User created = User.builder()
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .email(request.getEmail().trim())
                .telephone(request.getTelephone().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .subscriptionBypass(bypass)
                .build();

        created = userRepository.save(created);
        return new AdminUserDto(
                created.getId(),
                created.getNom(),
                created.getPrenom(),
                created.getEmail(),
                created.getTelephone(),
                primaryCountryForUser(created),
                0,
                0L,
                "NO_SUBSCRIPTION",
                null,
                created.isSubscriptionBypass(),
                profilePhotoBase64(created)
        );
    }

    @PutMapping("/users/{id}")
    public AdminUserDto updateUser(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserRequest request,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            if (request.getNom() != null && !request.getNom().isBlank()) user.setNom(request.getNom().trim());
            if (request.getPrenom() != null && !request.getPrenom().isBlank()) user.setPrenom(request.getPrenom().trim());
            if (request.getTelephone() != null && !request.getTelephone().isBlank()) user.setTelephone(request.getTelephone().trim());
            if (request.getSubscriptionBypass() != null) user.setSubscriptionBypass(request.getSubscriptionBypass());

            userRepository.save(user);

            SubscriptionInfo sub;
            try {
                sub = getSubscriptionInfo(user);
            } catch (Exception ignored) {
                sub = new SubscriptionInfo("ERROR", null);
            }

            String country;
            int orgCount;
            long menusCount;
            try {
                country = primaryCountryForUser(user);
                orgCount = organizationsCountForUser(user);
                menusCount = menusCountForUser(user);
            } catch (Exception ignored) {
                country = null;
                orgCount = 0;
                menusCount = 0L;
            }

            return new AdminUserDto(
                    user.getId(),
                    user.getNom(),
                    user.getPrenom(),
                    user.getEmail(),
                    user.getTelephone(),
                    country,
                    orgCount,
                    menusCount,
                    sub.status(),
                    sub.plan(),
                    user.isSubscriptionBypass(),
                    profilePhotoBase64(user)
            );
        } catch (Exception e) {
            // Ne jamais remonter un 500 brut côté admin : on renvoie un DTO minimal.
            // (Le frontend va quand même pouvoir rafraîchir la table.)
            User user = userRepository.findById(id).orElse(null);
            if (user == null) throw e;

            return new AdminUserDto(
                    user.getId(),
                    user.getNom(),
                    user.getPrenom(),
                    user.getEmail(),
                    user.getTelephone(),
                    primaryCountryForUser(user),
                    0,
                    0L,
                    "ERROR",
                    null,
                    user.isSubscriptionBypass(),
                    profilePhotoBase64(user)
            );
        }
    }

    @PostMapping("/users/{id}/password")
    public void resetPassword(
            @PathVariable Long id,
            @RequestBody AdminResetPasswordRequest request,
            Authentication authentication
    ) {
        requireAdmin(authentication);

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password required");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            Authentication authentication
    ) {
        requireAdmin(authentication);

        if (Objects.equals(id, null)) return ResponseEntity.badRequest().build();

        // Best-effort : évicter le cache des menus publics concernés.
        // (MenuPublicService met en cache les réponses sous "publicMenus".)
        try {
            var orgs = organizationRepository.findByOwnerId(id);
            var cache = cacheManager.getCache("publicMenus");
            if (cache != null) {
                for (var org : orgs) {
                    var menus = menuRepository.findByOrganizationId(org.getId());
                    for (var menu : menus) {
                        if (menu.getSlug() != null) cache.evict(menu.getSlug());
                    }
                }
            }
        } catch (Exception ignored) {
            // do nothing
        }

        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

