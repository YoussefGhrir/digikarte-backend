package ghrir.digikarte.controller;

import ghrir.digikarte.dto.admin.AdminUserOrganizationDto;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.UserRepository;
import ghrir.digikarte.service.AdminUserOrganizationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Séparé de {@link AdminController} : sous Spring WebMVC 7, le pattern
 * {@code /users/{id}/organizations} n’était pas résolu quand il partageait le même
 * type que {@code GET /api/admin/users} (fallback handler fichiers statiques → 500).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserOrganizationsController {

    private final UserRepository userRepository;
    private final AdminUserOrganizationsService adminUserOrganizationsService;

    private User requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String email = authentication.getName();
        User actor = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        if (!actor.isAdmin() && !actor.isSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return actor;
    }

    @GetMapping("/{userId}/organizations")
    public List<AdminUserOrganizationDto> listOrganizationsForUser(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return adminUserOrganizationsService.listOrganizationsAndMenusForOwner(target.getId());
    }
}
