package ghrir.digikarte.service;

import ghrir.digikarte.dto.admin.AdminUserOrganizationDto;
import ghrir.digikarte.dto.admin.AdminUserOrganizationMenuDto;
import ghrir.digikarte.repository.MenuRepository;
import ghrir.digikarte.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture organisations / menus pour le panneau admin : projections légères (pas de LOB logo, pas de graphe MenuItem).
 */
@Service
@RequiredArgsConstructor
public class AdminUserOrganizationsService {

    private final OrganizationRepository organizationRepository;
    private final MenuRepository menuRepository;

    @Transactional(readOnly = true)
    public List<AdminUserOrganizationDto> listOrganizationsAndMenusForOwner(Long ownerUserId) {
        List<OrganizationRepository.IdNameProjection> orgs =
                organizationRepository.findIdAndNameByOwnerIdForAdmin(ownerUserId);
        if (orgs == null || orgs.isEmpty()) {
            return List.of();
        }
        List<AdminUserOrganizationDto> out = new ArrayList<>();
        for (OrganizationRepository.IdNameProjection org : orgs) {
            List<AdminUserOrganizationMenuDto> menus = new ArrayList<>();
            for (MenuRepository.MenuSummaryView row : menuRepository.findByOrganizationIdOrderByIdAsc(org.getId())) {
                menus.add(new AdminUserOrganizationMenuDto(row.getId(), row.getTitle(), row.getSlug()));
            }
            out.add(new AdminUserOrganizationDto(org.getId(), org.getName(), menus));
        }
        return out;
    }
}
