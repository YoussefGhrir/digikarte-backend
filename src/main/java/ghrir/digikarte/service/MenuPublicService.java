package ghrir.digikarte.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import ghrir.digikarte.dto.MenuItemDto;
import ghrir.digikarte.dto.MenuPublicDto;
import ghrir.digikarte.entity.Menu;
import ghrir.digikarte.entity.Organization;
import ghrir.digikarte.repository.MenuRepository;
import ghrir.digikarte.service.OrganizationPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuPublicService {

    private final MenuRepository menuRepository;
    private final OrganizationPhotoService organizationPhotoService;
    private final BillingService billingService;

    @Transactional(readOnly = true)
    public MenuPublicDto getBySlug(String slug) {
        Menu menu = menuRepository.findBySlug(slug).orElseThrow(() -> new RuntimeException("Menu non trouvé"));
        Organization org = menu.getOrganization();

        // Vérifie que le propriétaire possède un abonnement actif ou en essai.
        if (org.getOwner() == null || org.getOwner().getStripeSubscriptionId() == null
                || org.getOwner().getStripeSubscriptionId().isBlank()) {
            throw new RuntimeException("Abonnement requis pour afficher ce menu.");
        }
        String subId = org.getOwner().getStripeSubscriptionId();
        try {
            Subscription sub = billingService.retrieveSubscription(subId);
            String status = sub.getStatus() != null ? sub.getStatus().toLowerCase() : "";
            if (!"active".equals(status) && !"trialing".equals(status)) {
                throw new RuntimeException("Abonnement inactif – menu non disponible.");
            }
        } catch (StripeException e) {
            throw new RuntimeException("Erreur de vérification d'abonnement Stripe.", e);
        }

        MenuPublicDto dto = new MenuPublicDto();
        dto.setTitle(menu.getTitle());
        dto.setDescription(menu.getDescription());
        dto.setOrganizationName(menu.getOrganization().getName());
        dto.setOrganizationSlogan(menu.getOrganization().getSlogan());
        dto.setOrganizationLogoBase64(organizationPhotoService.toBase64(menu.getOrganization().getLogo()));
        dto.setOrganizationAddress(formatOrganizationAddress(menu.getOrganization()));
        dto.setOrganizationPhone(menu.getOrganization().getPhone());
        dto.setOrganizationEmail(menu.getOrganization().getEmail());
        dto.setDisplayTemplate(menu.getDisplayTemplate());
        dto.setPriceCurrency(menu.getPriceCurrency() != null ? menu.getPriceCurrency() : "EUR");
        dto.setItems(menu.getItems().stream().map(item -> {
            MenuItemDto i = new MenuItemDto();
            i.setId(item.getId());
            i.setName(item.getName());
            i.setDescription(item.getDescription());
            i.setPrice(item.getPrice());
            i.setImageUrl(item.getImageUrl());
            i.setSection(item.getSection());
            i.setSortOrder(item.getSortOrder());
            i.setParentItemId(item.getParentItemId());
            return i;
        }).collect(Collectors.toList()));
        return dto;
    }

    /** Adresse sur une ou plusieurs lignes (rue, CP ville, pays) pour affichage footer. */
    private String formatOrganizationAddress(Organization org) {
        if (org.getAddressLine1() == null && org.getAddressPostalCode() == null
                && org.getAddressCity() == null && org.getCountry() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (org.getAddressLine1() != null && !org.getAddressLine1().isBlank()) {
            sb.append(org.getAddressLine1().trim());
        }
        boolean hasPostalCity = (org.getAddressPostalCode() != null && !org.getAddressPostalCode().isBlank())
                || (org.getAddressCity() != null && !org.getAddressCity().isBlank());
        if (hasPostalCity) {
            if (sb.length() > 0) sb.append(", ");
            if (org.getAddressPostalCode() != null) sb.append(org.getAddressPostalCode().trim());
            if (org.getAddressCity() != null && !org.getAddressCity().isBlank()) {
                if (org.getAddressPostalCode() != null && !org.getAddressPostalCode().isBlank()) sb.append(" ");
                sb.append(org.getAddressCity().trim());
            }
        }
        if (org.getCountry() != null && !org.getCountry().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(org.getCountry().trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
