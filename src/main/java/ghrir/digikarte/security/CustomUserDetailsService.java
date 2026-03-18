package ghrir.digikarte.security;

import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.UserRepository;
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
        Object[] creds = userRepository.findCredentialsByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé: " + email));
        String foundEmail = (String) creds[0];
        String foundPassword = (String) creds[1];
        return new org.springframework.security.core.userdetails.User(
                foundEmail,
                foundPassword,
                java.util.Collections.emptyList()
        );
    }
}
