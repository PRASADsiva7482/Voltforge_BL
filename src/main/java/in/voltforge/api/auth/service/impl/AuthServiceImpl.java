package in.voltforge.api.auth.service.impl;

import in.voltforge.api.auth.service.AuthService;
import in.voltforge.api.common.enums.AccountStatus;
import in.voltforge.api.common.enums.SubscriptionType;
import in.voltforge.api.common.enums.UserRole;
import in.voltforge.api.user.dto.UserResponse;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.mapper.UserMapper;
import in.voltforge.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse syncUser(Jwt jwt) {
        String keycloakId = extractKeycloakId(jwt);
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        String displayName = buildDisplayName(firstName, lastName, username);

        Optional<User> existingUser = userRepository.findByKeycloakId(keycloakId);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Update fields that may have changed in Keycloak
            user.setUsername(username != null ? username : user.getUsername());
            user.setEmail(email != null ? email : user.getEmail());
            if (displayName != null && !displayName.equals(user.getDisplayName())) {
                user.setDisplayName(displayName);
            }
            log.info("Synced existing user: {} ({})", username, keycloakId);
        } else {
            user = User.builder()
                    .keycloakId(keycloakId)
                    .username(username != null ? username : keycloakId)
                    .email(email != null ? email : username + "@voltforge.in")
                    .displayName(displayName)
                    .role(determineRole(jwt))
                    .subscriptionType(SubscriptionType.FREE)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();
            log.info("Created new user: {} ({})", username, keycloakId);
        }

        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Jwt jwt) {
        String keycloakId = extractKeycloakId(jwt);
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElse(null);

        if (user == null) {
            // Auto-sync if user doesn't exist locally yet
            return syncUser(jwt);
        }

        return userMapper.toResponse(user);
    }

    @Override
    public String extractKeycloakId(Jwt jwt) {
        return jwt.getSubject();
    }

    @SuppressWarnings("unchecked")
    private UserRole determineRole(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                if (roles != null && roles.stream().anyMatch(r -> r.equalsIgnoreCase("ADMIN"))) {
                    return UserRole.ADMIN;
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine role from JWT, defaulting to USER: {}", e.getMessage());
        }
        return UserRole.USER;
    }

    private String buildDisplayName(String firstName, String lastName, String username) {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null) return firstName;
        return username;
    }
}
