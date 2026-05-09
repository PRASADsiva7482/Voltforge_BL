package in.voltforge.api.subscription.repository;

import in.voltforge.api.common.enums.SubscriptionStatus;
import in.voltforge.api.common.enums.SubscriptionType;
import in.voltforge.api.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    Optional<Subscription> findByUserIdAndStatus(String userId, SubscriptionStatus status);

    List<Subscription> findByUserId(String userId);

    Optional<Subscription> findTopByUserIdOrderByCreatedAtDesc(String userId);

    long countByPlanType(SubscriptionType planType);

    long countByStatus(SubscriptionStatus status);

    @Query("SELECT s.planType, COUNT(s) FROM Subscription s WHERE s.status = 'ACTIVE' GROUP BY s.planType")
    List<Object[]> countActiveByPlanType();
}
