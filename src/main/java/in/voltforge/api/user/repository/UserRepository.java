package in.voltforge.api.user.repository;

import in.voltforge.api.common.enums.AccountStatus;
import in.voltforge.api.common.enums.UserRole;
import in.voltforge.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByKeycloakId(String keycloakId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRole(UserRole role);

    long countByAccountStatus(AccountStatus accountStatus);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= CURRENT_DATE")
    long countNewUsersToday();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= FUNCTION('DATE_SUB', CURRENT_DATE, 7)")
    long countNewUsersThisWeek();
}
