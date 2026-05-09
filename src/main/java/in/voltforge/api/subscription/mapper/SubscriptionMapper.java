package in.voltforge.api.subscription.mapper;

import in.voltforge.api.subscription.dto.SubscriptionResponse;
import in.voltforge.api.subscription.entity.Subscription;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    public SubscriptionResponse toResponse(Subscription subscription) {
        if (subscription == null) return null;
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .userId(subscription.getUser().getId())
                .planType(subscription.getPlanType())
                .status(subscription.getStatus())
                .startsAt(subscription.getStartsAt())
                .expiresAt(subscription.getExpiresAt())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
