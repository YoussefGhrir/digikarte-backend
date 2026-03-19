package ghrir.digikarte.controller;

import ghrir.digikarte.dto.AuthResponse;
import ghrir.digikarte.dto.LoginRequest;
import ghrir.digikarte.dto.ProfileDto;
import ghrir.digikarte.dto.RegisterRequest;
import ghrir.digikarte.exception.ImageTooLargeException;
import ghrir.digikarte.service.AuthService;
import ghrir.digikarte.service.ProfilePhotoService;
import ghrir.digikarte.service.GoogleOAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ProfilePhotoService profilePhotoService;
    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileDto> getProfile(Authentication authentication) {
        return ResponseEntity.ok(authService.getProfile(authentication));
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileDto> updateProfile(
            Authentication authentication,
            @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(authService.updateProfile(
                authentication,
                body.get("prenom"),
                body.get("nom"),
                body.get("telephone")
        ));
    }

    @PostMapping("/me/photo")
    public ResponseEntity<Void> updateProfilePhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        if (file.getSize() > ProfilePhotoService.getMaxInputBytes()) {
            throw new ImageTooLargeException("Image is too large. Please use an image under 5 MB.");
        }
        try {
            byte[] processed = profilePhotoService.processProfilePhoto(file.getInputStream(), file.getContentType());
            authService.updateProfilePhoto(authentication, processed);
        } catch (java.io.IOException e) {
            throw new ghrir.digikarte.exception.InvalidImageException("Could not read uploaded file.");
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(Authentication authentication) {
        authService.deleteCurrentUser(authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/google/login")
    public ResponseEntity<Void> googleLogin(
            @RequestParam(name = "lang", required = false) String lang,
            @RequestParam(name = "source", required = false) String source
    ) {
        String url = googleOAuthService.buildGoogleAuthUrl(lang, source);
        return ResponseEntity.status(302)
                .header("Location", url)
                .build();
    }

    @GetMapping("/google/login-url")
    public ResponseEntity<Map<String, String>> googleLoginUrl(
            @RequestParam(name = "lang", required = false) String lang,
            @RequestParam(name = "source", required = false) String source
    ) {
        String url = googleOAuthService.buildGoogleAuthUrl(lang, source);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam("code") String code,
            @RequestParam(name = "state", required = false) String state
    ) throws Exception {
        String lang = "de";
        String source = "login";
        if (state != null && !state.isBlank()) {
            String[] parts = state.split(":", 2);
            if (parts.length >= 1 && !parts[0].isBlank()) {
                lang = parts[0];
            }
            if (parts.length == 2 && !parts[1].isBlank()) {
                source = parts[1];
            }
        }

        boolean registerFlow = "register".equalsIgnoreCase(source);

        try {
            AuthResponse auth = googleOAuthService.handleCallback(code, registerFlow);

            String redirect = "https://www.digi-karte.com/login"
                    + "?googleToken=" + java.net.URLEncoder.encode(auth.getToken(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&lang=" + java.net.URLEncoder.encode(lang, java.nio.charset.StandardCharsets.UTF_8)
                    + "&email=" + java.net.URLEncoder.encode(auth.getEmail(), java.nio.charset.StandardCharsets.UTF_8)
                    + (registerFlow ? "&justRegistered=1" : "");

            return ResponseEntity.status(302)
                    .header("Location", redirect)
                    .build();
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            if (registerFlow && msg != null && msg.startsWith("EMAIL_ALREADY_EXISTS:")) {
                String email = msg.substring("EMAIL_ALREADY_EXISTS:".length());
                String redirect = "https://www.digi-karte.com/register"
                        + "?googleError=EMAIL_ALREADY_EXISTS"
                        + "&lang=" + java.net.URLEncoder.encode(lang, java.nio.charset.StandardCharsets.UTF_8)
                        + "&email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);

                return ResponseEntity.status(302)
                        .header("Location", redirect)
                        .build();
            }

            String baseRedirect = registerFlow
                    ? "https://www.digi-karte.com/register"
                    : "https://www.digi-karte.com/login";

            String redirect = baseRedirect
                    + "?googleError=OAUTH_FAILED"
                    + "&lang=" + java.net.URLEncoder.encode(lang, java.nio.charset.StandardCharsets.UTF_8);

            return ResponseEntity.status(302)
                    .header("Location", redirect)
                    .build();
        }
    }
}
