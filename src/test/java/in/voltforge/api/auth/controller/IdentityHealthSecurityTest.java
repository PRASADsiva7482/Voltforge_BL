package in.voltforge.api.auth.controller;

import in.voltforge.api.auth.service.AuthService;
import in.voltforge.api.auth.service.IdentityAvailabilityService;
import in.voltforge.api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = { AuthController.class, SecurityConfig.class })
class IdentityHealthSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean IdentityAvailabilityService identityAvailability;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void anonymousProbeReturnsOnlyConfiguredAvailability() throws Exception {
        when(identityAvailability.isAvailable()).thenReturn(true);
        when(identityAvailability.issuer()).thenReturn("https://identity.invalid/realms/test");
        mvc.perform(get("/api/v1/auth/identity-health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.issuer").value("https://identity.invalid/realms/test"));
    }

    @Test void offlineProbeIsUnavailable() throws Exception {
        mvc.perform(get("/api/v1/auth/identity-health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test void accountAndSyncStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/sync")).andExpect(status().isUnauthorized());
    }
}
