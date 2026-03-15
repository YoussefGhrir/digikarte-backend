package ghrir.digikarte.controller;

import ghrir.digikarte.dto.MenuDto;
import ghrir.digikarte.service.MenuService;
import ghrir.digikarte.service.QrCodeService;
import ghrir.digikarte.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final QrCodeService qrCodeService;
    private final UserRepository userRepository;

    private Long currentUserId(UserDetails user) {
        if (user == null) return null;
        return userRepository.findByEmail(user.getUsername()).map(u -> u.getId()).orElse(null);
    }

    @GetMapping
    public ResponseEntity<List<MenuDto>> list(
            @RequestParam Long organizationId,
            @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(menuService.findByOrganizationId(organizationId, userId));
    }

    @PostMapping
    public ResponseEntity<MenuDto> create(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        Long orgId = Long.valueOf(body.get("organizationId").toString());
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        String priceCurrency = body.get("priceCurrency") != null ? body.get("priceCurrency").toString().trim().toUpperCase() : "EUR";
        return ResponseEntity.ok(menuService.create(orgId, userId, title, description, priceCurrency));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuDto> get(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(menuService.getById(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuDto> update(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, String> body) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        String priceCurrency = body.get("priceCurrency");
        return ResponseEntity.ok(menuService.update(id, userId, body.get("title"), body.get("description"), body.get("displayTemplate"), priceCurrency));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        menuService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<MenuDto> addItem(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        BigDecimal price = body.get("price") != null ? new BigDecimal(body.get("price").toString()) : null;
        String imageUrl = (String) body.get("imageUrl");
        Integer sortOrder = body.get("sortOrder") != null ? (Integer) body.get("sortOrder") : null;
        String section = (String) body.get("section");
        Long parentItemId = body.get("parentItemId") != null ? Long.valueOf(body.get("parentItemId").toString()) : null;
        return ResponseEntity.ok(menuService.addItem(id, userId, name, description, price, imageUrl, sortOrder, section, parentItemId));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<MenuDto> updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Object> body) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        BigDecimal price = body.get("price") != null ? new BigDecimal(body.get("price").toString()) : null;
        String imageUrl = (String) body.get("imageUrl");
        Integer sortOrder = body.get("sortOrder") != null ? (Integer) body.get("sortOrder") : null;
        String section = (String) body.get("section");
        return ResponseEntity.ok(menuService.updateItem(id, itemId, userId, name, description, price, imageUrl, sortOrder, section));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<MenuDto> removeItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(menuService.removeItem(id, itemId, userId));
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<byte[]> getQr(
            @PathVariable Long id,
            @RequestParam(defaultValue = "256") int size,
            @RequestParam(required = false) String mode,
            @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        MenuDto menu = menuService.getById(id, userId);
        try {
            byte[] png = "high".equalsIgnoreCase(mode) ? qrCodeService.generatePngWithErrorLevel(menu.getSlug(), size, "H")
                    : "low".equalsIgnoreCase(mode) ? qrCodeService.generatePngWithErrorLevel(menu.getSlug(), size, "L")
                    : qrCodeService.generatePng(menu.getSlug(), size);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"menu-" + menu.getSlug() + ".png\"")
                    .body(png);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/qr-url")
    public ResponseEntity<Map<String, String>> getQrUrl(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        Long userId = currentUserId(user);
        if (userId == null) return ResponseEntity.status(401).build();
        MenuDto menu = menuService.getById(id, userId);
        String url = qrCodeService.getMenuPublicUrl(menu.getSlug());
        return ResponseEntity.ok(Map.of("url", url, "slug", menu.getSlug()));
    }
}
