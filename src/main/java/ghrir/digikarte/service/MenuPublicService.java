package ghrir.digikarte.service;

import ghrir.digikarte.dto.MenuItemDto;
import ghrir.digikarte.dto.MenuPublicDto;
import ghrir.digikarte.entity.Menu;
import ghrir.digikarte.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuPublicService {

    private final MenuRepository menuRepository;

    @Transactional(readOnly = true)
    public MenuPublicDto getBySlug(String slug) {
        Menu menu = menuRepository.findBySlug(slug).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        MenuPublicDto dto = new MenuPublicDto();
        dto.setTitle(menu.getTitle());
        dto.setDescription(menu.getDescription());
        dto.setOrganizationName(menu.getOrganization().getName());
        dto.setItems(menu.getItems().stream().map(item -> {
            MenuItemDto i = new MenuItemDto();
            i.setId(item.getId());
            i.setName(item.getName());
            i.setDescription(item.getDescription());
            i.setPrice(item.getPrice());
            i.setImageUrl(item.getImageUrl());
            i.setSortOrder(item.getSortOrder());
            return i;
        }).collect(Collectors.toList()));
        return dto;
    }
}
