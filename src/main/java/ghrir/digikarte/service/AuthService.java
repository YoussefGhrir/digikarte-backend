package ghrir.digikarte.service;

import ghrir.digikarte.dto.AuthResponse;
import ghrir.digikarte.dto.LoginRequest;
import ghrir.digikarte.dto.RegisterRequest;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.exception.EmailAlreadyExistsException;
import ghrir.digikarte.repository.UserRepository;
import ghrir.digikarte.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Un compte existe déjà avec cet email");
        }
        User user = User.builder()
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .email(request.getEmail())
                .telephone(request.getTelephone())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .userId(user.getId())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .userId(user.getId())
                .build();
    }

    public void deleteCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        userRepository.delete(user);
    }
}
