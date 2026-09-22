package in.voltforge.api.auth.controller;

import in.voltforge.api.auth.service.AuthService;
import in.voltforge.api.auth.service.IdentityAvailabilityService;
import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user sync APIs")
public class AuthController {

    private final AuthService authService;
    private final IdentityAvailabilityService identityAvailability;

    public record IdentityHealth(String issuer, boolean available) {}

    @GetMapping("/identity-health")
    @Operation(summary = "Check the configured sign-in provider without browser discovery CORS")
    public ResponseEntity<ApiResponse<IdentityHealth>> identityHealth() {
        boolean available = identityAvailability.isAvailable();
        return ResponseEntity.status(available ? 200 : 503)
                .header("Cache-Control", "no-store")
                .body(ApiResponse.success(new IdentityHealth(identityAvailability.issuer(), available)));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync Keycloak user to local database")
    public ResponseEntity<ApiResponse<UserResponse>> syncUser(@AuthenticationPrincipal Jwt jwt) {
        UserResponse user = authService.syncUser(jwt);
        return ResponseEntity.ok(ApiResponse.success("User synchronized successfully", user));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user details")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UserResponse user = authService.getCurrentUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/health")
    @Operation(summary = "Auth service health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("VoltForge Auth Service is running"));
    }
}
