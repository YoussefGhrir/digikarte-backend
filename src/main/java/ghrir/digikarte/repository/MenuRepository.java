package ghrir.digikarte.repository;

import ghrir.digikarte.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    interface MenuSummaryView {
        Long getId();
        String getTitle();
        String getSlug();
        String getDisplayTemplate();
        String getPriceCurrency();
    }

    List<Menu> findByOrganizationId(Long organizationId);

    long countByOrganizationId(Long organizationId);

    interface OrgMenuCountView {
        Long getOrganizationId();
        Long getCount();
    }

    @Query("select m.organization.id as organizationId, count(m) as count from Menu m where m.organization.id in :organizationIds group by m.organization.id")
    List<OrgMenuCountView> countByOrganizationIds(List<Long> organizationIds);

    List<MenuSummaryView> findByOrganizationIdOrderByIdAsc(Long organizationId);

    /**
     * Projection minimale pour l’admin (évite les écarts de schéma / bugs de projection sur toutes les colonnes).
     */
    interface AdminUserMenuBrief {
        Long getId();
        String getTitle();
        String getSlug();
    }

    @Query(
            "select m.id as id, m.title as title, m.slug as slug from Menu m "
                    + "where m.organization.id = :organizationId order by m.id asc"
    )
    List<AdminUserMenuBrief> findAdminBriefMenusByOrganizationId(@Param("organizationId") Long organizationId);

    Optional<Menu> findBySlug(String slug);
}
