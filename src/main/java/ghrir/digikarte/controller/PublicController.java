package ghrir.digikarte.controller;

import ghrir.digikarte.dto.MenuPublicDto;
import ghrir.digikarte.service.MenuPublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final MenuPublicService menuPublicService;

    @GetMapping("/menu/{slug}")
    public ResponseEntity<MenuPublicDto> getMenuBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(menuPublicService.getBySlug(slug));
    }
}
