package ghrir.digikarte.repository;

import ghrir.digikarte.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    Optional<Menu> findBySlug(String slug);
}
