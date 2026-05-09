package in.voltforge.api.auth.service;

import in.voltforge.api.user.dto.UserResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {

    /**
     * Synchronizes the Keycloak user to the local database.
     * Creates a new user record if one doesn't exist, or updates the existing one.
     */
    UserResponse syncUser(Jwt jwt);

    /**
     * Gets the current authenticated user's details from the local database.
     */
    UserResponse getCurrentUser(Jwt jwt);

    /**
     * Extracts the Keycloak user ID (subject) from the JWT token.
     */
    String extractKeycloakId(Jwt jwt);
}
