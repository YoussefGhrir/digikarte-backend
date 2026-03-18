package ghrir.digikarte.repository;

import ghrir.digikarte.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Variante optimisée pour l'auth qui ne charge pas le LOB profilePhoto.
     */
    @Query("select u.id, u.email, u.password from User u where u.email = :email")
    Optional<Object[]> findCredentialsByEmail(@Param("email") String email);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
