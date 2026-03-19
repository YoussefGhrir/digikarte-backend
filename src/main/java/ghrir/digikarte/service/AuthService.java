package ghrir.digikarte.service;

import ghrir.digikarte.dto.AuthResponse;
import ghrir.digikarte.dto.LoginRequest;
import ghrir.digikarte.dto.ProfileDto;
import ghrir.digikarte.dto.RegisterRequest;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.exception.EmailAlreadyExistsException;
import ghrir.digikarte.repository.UserRepository;
import ghrir.digikarte.security.JwtService;
import ghrir.digikarte.service.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ProfilePhotoService profilePhotoService;

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
        return generateAuthResponseForUser(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        String lookup = request.getEmail();
        if (lookup != null && !lookup.contains("@")) {
            if (lookup.equalsIgnoreCase(AdminBootstrapService.DEFAULT_ADMIN_USERNAME)) {
                lookup = AdminBootstrapService.DEFAULT_ADMIN_EMAIL;
            }
        }
        User user = userRepository.findByEmail(lookup).orElseThrow();
        return generateAuthResponseForUser(user);
    }

    public AuthResponse generateAuthResponseForUser(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .userId(user.getId())
                .subscriptionBypass(user.isSubscriptionBypass())
                .admin(user.isAdmin())
                .superAdmin(user.isSuperAdmin())
                .build();
    }

    public void deleteCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        userRepository.delete(user);
    }

    public ProfileDto getProfile(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        String photoB64 = user.getProfilePhoto() != null && user.getProfilePhoto().length > 0
                ? Base64.getEncoder().encodeToString(user.getProfilePhoto())
                : null;
        return ProfileDto.builder()
                .email(user.getEmail())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .telephone(user.getTelephone())
                .profilePhotoBase64(photoB64)
                .subscriptionBypass(user.isSubscriptionBypass())
                .admin(user.isAdmin())
                .superAdmin(user.isSuperAdmin())
                .build();
    }

    public ProfileDto updateProfile(Authentication authentication, String prenom, String nom, String telephone) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        if (prenom != null && !prenom.isBlank()) user.setPrenom(prenom.trim());
        if (nom != null && !nom.isBlank()) user.setNom(nom.trim());
        if (telephone != null) user.setTelephone(telephone.trim());
        user = userRepository.save(user);
        String photoB64 = user.getProfilePhoto() != null && user.getProfilePhoto().length > 0
                ? Base64.getEncoder().encodeToString(user.getProfilePhoto())
                : null;
        return ProfileDto.builder()
                .email(user.getEmail())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .telephone(user.getTelephone())
                .profilePhotoBase64(photoB64)
                .subscriptionBypass(user.isSubscriptionBypass())
                .admin(user.isAdmin())
                .superAdmin(user.isSuperAdmin())
                .build();
    }

    public void updateProfilePhoto(Authentication authentication, byte[] processedPhoto) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setProfilePhoto(processedPhoto);
        userRepository.save(user);
    }
}
