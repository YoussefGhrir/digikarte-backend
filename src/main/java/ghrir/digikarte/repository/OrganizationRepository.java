package ghrir.digikarte.repository;

import ghrir.digikarte.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /**
     * Projection admin : id + nom uniquement (évite le LOB logo et le chargement des menus côté Organization).
     */
    interface IdNameProjection {
        Long getId();
        String getName();
    }

    @Query("select o.id as id, o.name as name from Organization o where o.owner.id = :ownerId order by o.id asc")
    List<IdNameProjection> findIdAndNameByOwnerIdForAdmin(@Param("ownerId") Long ownerId);

    List<Organization> findByOwnerId(Long ownerId);
    List<Organization> findByOwnerIdIn(List<Long> ownerIds);
}
