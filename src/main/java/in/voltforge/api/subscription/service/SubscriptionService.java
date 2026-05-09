package in.voltforge.api.subscription.service;

import in.voltforge.api.subscription.dto.SubscriptionResponse;
import in.voltforge.api.subscription.dto.UpgradeSubscriptionRequest;

public interface SubscriptionService {

    SubscriptionResponse getCurrentSubscription(String keycloakId);

    SubscriptionResponse upgradeSubscription(String keycloakId, UpgradeSubscriptionRequest request);
}
