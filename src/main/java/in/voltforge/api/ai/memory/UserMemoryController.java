package in.voltforge.api.ai.memory;

import in.voltforge.api.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/ai/user-memory") @RequiredArgsConstructor
public class UserMemoryController {
    private final UserMemoryService service;
    public record Preference(@NotBlank @Size(max=32) String value) {}
    @GetMapping public ApiResponse<Map<String,String>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Approved preferences loaded",service.list(jwt.getSubject()));
    }
    @PutMapping("/{key}") public ApiResponse<Map<String,String>> put(@AuthenticationPrincipal Jwt jwt,@PathVariable String key,@Valid @RequestBody Preference body) {
        return ApiResponse.success("Preference saved",service.put(jwt.getSubject(),key,body.value()));
    }
    @DeleteMapping("/{key}") public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable String key) {
        service.delete(jwt.getSubject(),key); return ApiResponse.success("Preference deleted",null);
    }
}
