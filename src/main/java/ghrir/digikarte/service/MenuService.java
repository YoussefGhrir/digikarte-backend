package ghrir.digikarte.service;

import ghrir.digikarte.dto.MenuDto;
import ghrir.digikarte.dto.MenuItemDto;
import ghrir.digikarte.entity.Menu;
import ghrir.digikarte.entity.MenuItem;
import ghrir.digikarte.entity.Organization;
import ghrir.digikarte.repository.MenuRepository;
import ghrir.digikarte.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public List<MenuDto> findByOrganizationId(Long organizationId, Long userId) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        return menuRepository.findByOrganizationId(organizationId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MenuDto create(Long organizationId, Long userId, String title, String description) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        Menu menu = Menu.builder()
                .title(title)
                .description(description)
                .organization(org)
                .build();
        menu = menuRepository.save(menu);
        return toDto(menu);
    }

    @Transactional
    public MenuDto update(Long menuId, Long userId, String title, String description) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        if (title != null) menu.setTitle(title);
        if (description != null) menu.setDescription(description);
        return toDto(menuRepository.save(menu));
    }

    @Transactional
    public void delete(Long menuId, Long userId) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        menuRepository.delete(menu);
    }

    @Transactional
    public MenuDto addItem(Long menuId, Long userId, String name, String description, BigDecimal price, String imageUrl, Integer sortOrder) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        MenuItem item = MenuItem.builder()
                .name(name)
                .description(description)
                .price(price)
                .imageUrl(imageUrl)
                .sortOrder(sortOrder != null ? sortOrder : menu.getItems().size())
                .menu(menu)
                .build();
        menu.getItems().add(item);
        menuRepository.save(menu);
        return toDto(menuRepository.findById(menuId).orElseThrow());
    }

    @Transactional
    public MenuDto updateItem(Long menuId, Long itemId, Long userId, String name, String description, BigDecimal price, String imageUrl, Integer sortOrder) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        MenuItem item = menu.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new RuntimeException("Élément non trouvé"));
        if (name != null) item.setName(name);
        if (description != null) item.setDescription(description);
        if (price != null) item.setPrice(price);
        if (imageUrl != null) item.setImageUrl(imageUrl);
        if (sortOrder != null) item.setSortOrder(sortOrder);
        menuRepository.save(menu);
        return toDto(menuRepository.findById(menuId).orElseThrow());
    }

    @Transactional
    public MenuDto removeItem(Long menuId, Long itemId, Long userId) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        menu.getItems().removeIf(i -> i.getId().equals(itemId));
        menuRepository.save(menu);
        return toDto(menuRepository.findById(menuId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public MenuDto getById(Long menuId, Long userId) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        return toDto(menu);
    }

    private MenuDto toDto(Menu menu) {
        MenuDto dto = new MenuDto();
        dto.setId(menu.getId());
        dto.setTitle(menu.getTitle());
        dto.setDescription(menu.getDescription());
        dto.setSlug(menu.getSlug());
        dto.setOrganizationId(menu.getOrganization().getId());
        dto.setItems(menu.getItems().stream().map(this::toItemDto).collect(Collectors.toList()));
        return dto;
    }

    private MenuItemDto toItemDto(MenuItem item) {
        MenuItemDto dto = new MenuItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setPrice(item.getPrice());
        dto.setImageUrl(item.getImageUrl());
        dto.setSortOrder(item.getSortOrder());
        return dto;
    }
}
