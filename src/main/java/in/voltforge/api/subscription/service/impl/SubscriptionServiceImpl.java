package in.voltforge.api.subscription.service.impl;

import in.voltforge.api.common.enums.SubscriptionStatus;
import in.voltforge.api.common.enums.SubscriptionType;
import in.voltforge.api.common.exception.BadRequestException;
import in.voltforge.api.common.exception.ResourceNotFoundException;
import in.voltforge.api.subscription.dto.SubscriptionResponse;
import in.voltforge.api.subscription.dto.UpgradeSubscriptionRequest;
import in.voltforge.api.subscription.entity.Subscription;
import in.voltforge.api.subscription.mapper.SubscriptionMapper;
import in.voltforge.api.subscription.repository.SubscriptionRepository;
import in.voltforge.api.subscription.service.SubscriptionService;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));

        Optional<Subscription> subscription = subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);

        if (subscription.isPresent()) {
            return subscriptionMapper.toResponse(subscription.get());
        }

        // Return a default FREE subscription response
        return SubscriptionResponse.builder()
                .userId(user.getId())
                .planType(SubscriptionType.FREE)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    @Override
    @Transactional
    public SubscriptionResponse upgradeSubscription(String keycloakId, UpgradeSubscriptionRequest request) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));

        if (request.getPlanType() == SubscriptionType.FREE) {
            throw new BadRequestException("Cannot upgrade to FREE plan. Use downgrade instead.");
        }

        // Expire any existing active subscription
        Optional<Subscription> existingActive = subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
        existingActive.ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
        });

        // Create new subscription
        Subscription subscription = Subscription.builder()
                .user(user)
                .planType(request.getPlanType())
                .status(SubscriptionStatus.ACTIVE)
                .startsAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .paymentRef(request.getPaymentRef())
                .build();

        subscription = subscriptionRepository.save(subscription);

        // Update user's subscription type
        user.setSubscriptionType(request.getPlanType());
        userRepository.save(user);

        log.info("User '{}' upgraded to {} plan", user.getUsername(), request.getPlanType());
        return subscriptionMapper.toResponse(subscription);
    }
}
