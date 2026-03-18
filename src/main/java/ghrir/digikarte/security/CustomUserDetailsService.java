package ghrir.digikarte.security;

import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.UserRepository;
import ghrir.digikarte.service.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Compatibilité: on permet de se connecter avec "gharghour" (sans @),
        // en le mappant sur le vrai email de l'admin (respecte @Email côté DB).
        String lookup = email;
        if (lookup != null && !lookup.contains("@")) {
            if (lookup.equalsIgnoreCase(AdminBootstrapService.DEFAULT_ADMIN_USERNAME)) {
                lookup = AdminBootstrapService.DEFAULT_ADMIN_EMAIL;
            }
        }

        UserRepository.CredentialsView creds = userRepository.findCredentialsByEmail(lookup)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé: " + email));
        return new org.springframework.security.core.userdetails.User(
                creds.getEmail(),
                creds.getPassword(),
                java.util.Collections.emptyList()
        );
    }
}
