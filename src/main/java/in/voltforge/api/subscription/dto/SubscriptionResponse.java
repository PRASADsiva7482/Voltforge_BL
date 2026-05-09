package in.voltforge.api.subscription.dto;

import in.voltforge.api.common.enums.SubscriptionStatus;
import in.voltforge.api.common.enums.SubscriptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private String id;
    private String userId;
    private SubscriptionType planType;
    private SubscriptionStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
