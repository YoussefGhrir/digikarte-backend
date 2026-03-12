package ghrir.digikarte.repository;

import ghrir.digikarte.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findByOrganizationId(Long organizationId);
    Optional<Menu> findBySlug(String slug);
}
