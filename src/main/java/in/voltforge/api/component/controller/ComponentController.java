package in.voltforge.api.component.controller;

import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.component.dto.ComponentResponse;
import in.voltforge.api.component.dto.CustomComponentRequest;
import in.voltforge.api.component.service.ComponentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/components")
@RequiredArgsConstructor
@Tag(name = "Components", description = "Electronics component library APIs")
public class ComponentController {

    private final ComponentService componentService;

    @GetMapping
    @Operation(summary = "Get all components")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> getAllComponents() {
        List<ComponentResponse> components = componentService.getAllComponents();
        return ResponseEntity.ok(ApiResponse.success(components));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get components by category")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> getByCategory(@PathVariable String category) {
        List<ComponentResponse> components = componentService.getComponentsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(components));
    }

    @GetMapping("/free")
    @Operation(summary = "Get free (non-premium) components")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> getFreeComponents() {
        List<ComponentResponse> components = componentService.getFreeComponents();
        return ResponseEntity.ok(ApiResponse.success(components));
    }

    @GetMapping("/search")
    @Operation(summary = "Search components by name or description")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> searchComponents(@RequestParam String query) {
        List<ComponentResponse> components = componentService.searchComponents(query);
        return ResponseEntity.ok(ApiResponse.success(components));
    }

    @GetMapping("/{componentId}")
    @Operation(summary = "Get component by ID")
    public ResponseEntity<ApiResponse<ComponentResponse>> getComponentById(@PathVariable String componentId) {
        ComponentResponse component = componentService.getComponentById(componentId);
        return ResponseEntity.ok(ApiResponse.success(component));
    }

    @GetMapping("/categories")
    @Operation(summary = "Get all component categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories() {
        List<String> categories = componentService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @PostMapping("/custom")
    @Operation(summary = "Create a custom SVG-backed component")
    public ResponseEntity<ApiResponse<ComponentResponse>> createCustomComponent(@Valid @RequestBody CustomComponentRequest request) {
        ComponentResponse component = componentService.createCustomComponent(request);
        return ResponseEntity.ok(ApiResponse.success("Custom component created", component));
    }

    @GetMapping("/community")
    @Operation(summary = "Get community-published custom components")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> getCommunityComponents() {
        return ResponseEntity.ok(ApiResponse.success(componentService.getCommunityComponents()));
    }
}
