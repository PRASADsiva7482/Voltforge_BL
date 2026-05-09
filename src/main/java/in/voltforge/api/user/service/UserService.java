package in.voltforge.api.user.service;

import in.voltforge.api.user.dto.UpdateProfileRequest;
import in.voltforge.api.user.dto.UserResponse;

public interface UserService {

    UserResponse getProfile(String keycloakId);

    UserResponse updateProfile(String keycloakId, UpdateProfileRequest request);

    UserResponse getUserById(String userId);
}
