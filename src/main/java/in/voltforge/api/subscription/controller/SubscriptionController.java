package in.voltforge.api.subscription.controller;

import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.subscription.dto.SubscriptionResponse;
import in.voltforge.api.subscription.dto.UpgradeSubscriptionRequest;
import in.voltforge.api.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Subscription management APIs")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/current")
    @Operation(summary = "Get current subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getCurrentSubscription(
            @AuthenticationPrincipal Jwt jwt) {
        SubscriptionResponse subscription = subscriptionService.getCurrentSubscription(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(subscription));
    }

    @PostMapping("/upgrade")
    @Operation(summary = "Upgrade subscription plan")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> upgradeSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpgradeSubscriptionRequest request) {
        SubscriptionResponse subscription = subscriptionService.upgradeSubscription(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Subscription upgraded successfully", subscription));
    }
}
