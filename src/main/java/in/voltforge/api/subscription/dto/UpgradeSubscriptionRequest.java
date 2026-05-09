package in.voltforge.api.subscription.dto;

import in.voltforge.api.common.enums.SubscriptionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeSubscriptionRequest {

    @NotNull(message = "Plan type is required")
    private SubscriptionType planType;

    private String paymentRef;
}
