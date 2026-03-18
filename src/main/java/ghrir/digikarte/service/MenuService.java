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

    /**
     * Version allégée pour la liste des menus (dashboard) :
     * renvoie seulement les infos de base + nombre d'items, sans charger tous les éléments.
     */
    @Transactional(readOnly = true)
    public List<MenuDto> findSummariesByOrganizationId(Long organizationId, Long userId) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        return menuRepository.findByOrganizationIdOrderByIdAsc(organizationId).stream()
                .map(view -> {
                    MenuDto dto = new MenuDto();
                    dto.setId(view.getId());
                    dto.setTitle(view.getTitle());
                    dto.setSlug(view.getSlug());
                    dto.setOrganizationId(organizationId);
                    dto.setDisplayTemplate(view.getDisplayTemplate());
                    dto.setPriceCurrency(view.getPriceCurrency() != null ? view.getPriceCurrency() : "EUR");
                    dto.setItems(List.of()); // items non chargés ici
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public MenuDto create(Long organizationId, Long userId, String title, String description, String priceCurrency) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        String safeTitle = (title != null && !title.isBlank()) ? title.trim() : "";
        String safeDescription = (description != null && !description.isBlank()) ? description.trim() : null;
        String currency = (priceCurrency != null && !priceCurrency.isBlank()) ? priceCurrency.trim().toUpperCase() : "EUR";
        Menu menu = Menu.builder()
                .title(safeTitle)
                .description(safeDescription)
                .organization(org)
                .priceCurrency(currency)
                .build();
        menu = menuRepository.save(menu);
        return toDto(menu);
    }

    /** Met à jour titre, description et template. Le slug n’est jamais modifié (QR fixe pour impression). */
    @Transactional
    public MenuDto update(Long menuId, Long userId, String title, String description, String displayTemplate, String priceCurrency) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        if (title != null) menu.setTitle(title);
        if (description != null) menu.setDescription(description);
        if (displayTemplate != null) menu.setDisplayTemplate(displayTemplate);
        if (priceCurrency != null && !priceCurrency.isBlank()) menu.setPriceCurrency(priceCurrency.trim().toUpperCase());
        // slug intentionnellement jamais modifié : le QR /menu/{slug} reste valide à vie
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
    public MenuDto addItem(Long menuId, Long userId, String name, String description, BigDecimal price, String imageUrl, Integer sortOrder, String section, Long parentItemId) {
        Menu menu = menuRepository.findById(menuId).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        if (!menu.getOrganization().getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        MenuItem item = MenuItem.builder()
                .name(name)
                .description(description)
                .price(price)
                .imageUrl(imageUrl)
                .menu(menu)
                .build();
        if (parentItemId != null) {
            MenuItem parent = menu.getItems().stream().filter(i -> i.getId().equals(parentItemId)).findFirst()
                    .orElseThrow(() -> new RuntimeException("Plat parent introuvable"));
            if (parent.getParentItemId() != null) {
                throw new RuntimeException("Un sous-produit ne peut pas avoir de sous-produit");
            }
            item.setSection(parent.getSection());
            item.setParentItemId(parentItemId);
            int maxAfterParent = parent.getSortOrder();
            for (MenuItem x : menu.getItems()) {
                if (parentItemId.equals(x.getParentItemId())) {
                    maxAfterParent = Math.max(maxAfterParent, x.getSortOrder());
                }
            }
            int newOrder = maxAfterParent + 1;
            for (MenuItem x : menu.getItems()) {
                if (x.getSortOrder() >= newOrder) {
                    x.setSortOrder(x.getSortOrder() + 1);
                }
            }
            item.setSortOrder(newOrder);
        } else {
            item.setSection(section);
            item.setSortOrder(sortOrder != null ? sortOrder : menu.getItems().size());
        }
        menu.getItems().add(item);
        menuRepository.save(menu);
        return toDto(menuRepository.findById(menuId).orElseThrow());
    }

    @Transactional
    public MenuDto updateItem(Long menuId, Long itemId, Long userId, String name, String description, BigDecimal price, String imageUrl, Integer sortOrder, String section) {
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
        if (section != null) item.setSection(section);
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
        menu.getItems().removeIf(i -> itemId.equals(i.getParentItemId()));
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
        dto.setDisplayTemplate(menu.getDisplayTemplate());
        dto.setPriceCurrency(menu.getPriceCurrency() != null ? menu.getPriceCurrency() : "EUR");
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
        dto.setSection(item.getSection());
        dto.setSortOrder(item.getSortOrder());
        dto.setParentItemId(item.getParentItemId());
        return dto;
    }
}
