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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.keycloakId = :subject")
    Optional<User> lockMemoryOwner(String subject);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakIdOrUsernameOrEmail(String keycloakId, String username, String email);

    @Query("""
            SELECT new in.voltforge.api.user.repository.UserStatsRow(
                COUNT(u),
                COALESCE(SUM(CASE WHEN u.accountStatus = in.voltforge.api.common.enums.AccountStatus.ACTIVE THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN u.createdAt >= CURRENT_DATE THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN u.role = in.voltforge.api.common.enums.UserRole.ADMIN THEN 1L ELSE 0L END), 0L)
            )
            FROM User u
            """)
    UserStatsRow getUserStatsAggregate();

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
