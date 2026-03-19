package ghrir.digikarte.service;

import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class AdminBootstrapService {

    // Identifiants demandés côté login : "gharghour" / "gharghour"
    // Mais côté DB, User.email doit respecter la contrainte @Email.
    public static final String DEFAULT_ADMIN_USERNAME = "gharghour";
    public static final String DEFAULT_ADMIN_EMAIL = "gharghour@digikarte.local";
    public static final String DEFAULT_ADMIN_PASSWORD = "gharghour";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void ensureDefaultAdminUserExists() {
        userRepository.findByEmail(DEFAULT_ADMIN_EMAIL).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (!existing.isAdmin()) {
                existing.setAdmin(true);
                changed = true;
            }
            if (!existing.isSuperAdmin()) {
                existing.setSuperAdmin(true);
                changed = true;
            }
            if (!existing.isSubscriptionBypass()) {
                existing.setSubscriptionBypass(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(existing);
            }
        }, () -> {
            User admin = User.builder()
                    .nom("Admin")
                    .prenom("Owner")
                    .email(DEFAULT_ADMIN_EMAIL)
                    .telephone("0000000000")
                    .password(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                    .subscriptionBypass(true) // accès direct au dashboard admin
                    .isAdmin(true)
                    .isSuperAdmin(true)
                    .build();

            userRepository.save(admin);
        });
    }
}

