package ghrir.digikarte.repository;

import ghrir.digikarte.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<MenuSummaryView> findByOrganizationIdOrderByIdAsc(Long organizationId);

    Optional<Menu> findBySlug(String slug);
}
