package ghrir.digikarte.service;

import ghrir.digikarte.dto.admin.AdminUserOrganizationDto;
import ghrir.digikarte.dto.admin.AdminUserOrganizationMenuDto;
import ghrir.digikarte.entity.Menu;
import ghrir.digikarte.entity.Organization;
import ghrir.digikarte.repository.MenuRepository;
import ghrir.digikarte.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture organisations / menus pour le panneau super-admin (transaction dédiée, pas de projection JPQL fragile).
 */
@Service
@RequiredArgsConstructor
public class AdminUserOrganizationsService {

    private final OrganizationRepository organizationRepository;
    private final MenuRepository menuRepository;

    @Transactional(readOnly = true)
    public List<AdminUserOrganizationDto> listOrganizationsAndMenusForOwner(Long ownerUserId) {
        List<Organization> orgs = organizationRepository.findByOwnerId(ownerUserId);
        if (orgs == null || orgs.isEmpty()) {
            return List.of();
        }
        List<AdminUserOrganizationDto> out = new ArrayList<>();
        for (Organization org : orgs) {
            List<AdminUserOrganizationMenuDto> menus = new ArrayList<>();
            for (Menu m : menuRepository.findMenusForAdminByOrganizationId(org.getId())) {
                menus.add(new AdminUserOrganizationMenuDto(m.getId(), m.getTitle(), m.getSlug()));
            }
            out.add(new AdminUserOrganizationDto(org.getId(), org.getName(), menus));
        }
        return out;
    }
}
