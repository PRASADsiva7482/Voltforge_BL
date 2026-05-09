package in.voltforge.api.user.dto;

import in.voltforge.api.common.enums.AccountStatus;
import in.voltforge.api.common.enums.SubscriptionType;
import in.voltforge.api.common.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String keycloakId;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private UserRole role;
    private SubscriptionType subscriptionType;
    private AccountStatus accountStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
