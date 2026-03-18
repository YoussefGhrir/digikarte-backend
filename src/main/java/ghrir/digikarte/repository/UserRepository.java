package ghrir.digikarte.repository;

import ghrir.digikarte.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Projection légère pour l'authentification qui ne charge pas le LOB profilePhoto.
     */
    interface CredentialsView {
        String getEmail();
        String getPassword();
    }

    @Query("select u.email as email, u.password as password from User u where u.email = :email")
    Optional<CredentialsView> findCredentialsByEmail(@Param("email") String email);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
