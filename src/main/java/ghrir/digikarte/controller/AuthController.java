package ghrir.digikarte.controller;

import ghrir.digikarte.dto.AuthResponse;
import ghrir.digikarte.dto.LoginRequest;
import ghrir.digikarte.dto.ProfileDto;
import ghrir.digikarte.dto.RegisterRequest;
import ghrir.digikarte.exception.ImageTooLargeException;
import ghrir.digikarte.service.AuthService;
import ghrir.digikarte.service.ProfilePhotoService;
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
}
