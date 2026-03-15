package ghrir.digikarte.controller;

import ghrir.digikarte.dto.OrganizationDto;
import ghrir.digikarte.exception.ImageTooLargeException;
import ghrir.digikarte.exception.InvalidImageException;
import ghrir.digikarte.service.OrganizationPhotoService;
import ghrir.digikarte.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationPhotoService organizationPhotoService;
    private final ghrir.digikarte.repository.UserRepository userRepository;

    private Long currentUserId(UserDetails user) {
        if (user == null) return null;
        return userRepository.findByEmail(user.getUsername()).map(u -> u.getId()).orElse(null);
    }

    @GetMapping
    public ResponseEntity<List<OrganizationDto>> list(@AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(organizationService.findByOwnerId(userId));
    }

    @PostMapping
    public ResponseEntity<OrganizationDto> create(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, String> body) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(organizationService.create(
                userId,
                body.get("name"),
                body.get("slogan"),
                body.get("addressLine1"),
                body.get("addressPostalCode"),
                body.get("addressCity"),
                body.get("country"),
                body.get("phone"),
                body.get("email")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationDto> get(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(organizationService.getById(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganizationDto> update(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, String> body) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(organizationService.update(
                id, userId,
                body.get("name"),
                body.get("slogan"),
                body.get("addressLine1"),
                body.get("addressPostalCode"),
                body.get("addressCity"),
                body.get("country"),
                body.get("phone"),
                body.get("email")));
    }

    @PostMapping("/{id}/photo")
    public ResponseEntity<Void> updatePhoto(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user,
            @RequestParam("file") MultipartFile file) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        if (file.isEmpty()) {
            throw new InvalidImageException("No file provided");
        }
        if (file.getSize() > OrganizationPhotoService.getMaxInputBytes()) {
            throw new ImageTooLargeException("Image is too large. Max 15 MB.");
        }
        try {
            byte[] processed = organizationPhotoService.processOrganizationLogo(file.getInputStream(), file.getContentType());
            organizationService.updateLogo(id, userId, processed);
            return ResponseEntity.ok().build();
        } catch (InvalidImageException | ImageTooLargeException e) {
            throw e;
        } catch (org.springframework.dao.DataAccessException e) {
            throw new InvalidImageException("Could not save logo. Please try again.");
        } catch (Exception e) {
            throw new InvalidImageException("Could not read or process the uploaded file.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        organizationService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
