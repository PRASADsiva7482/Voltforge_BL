package in.voltforge.api.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Project Export", description = "Project export and utility APIs")
public class ProjectExportController {

    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{projectId}/bom")
    @Operation(summary = "Generate Bill of Materials for a project")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBom(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) {
        var project = projectService.getProject(projectId, jwt != null ? jwt.getSubject() : null);
        List<Map<String, Object>> bom = new ArrayList<>();

        if (project.getCanvasLayout() != null) {
            @SuppressWarnings("unchecked")
            var nodes = (List<Map<String, Object>>) project.getCanvasLayout().get("nodes");
            if (nodes != null) {
                Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
                for (Map<String, Object> node : nodes) {
                    String type = String.valueOf(node.getOrDefault("type", "UNKNOWN"));
                    if (grouped.containsKey(type)) {
                        Map<String, Object> existing = grouped.get(type);
                        existing.put("quantity", (int) existing.getOrDefault("quantity", 1) + 1);
                    } else {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("type", type);
                        item.put("name", node.getOrDefault("name", type));
                        item.put("quantity", 1);
                        item.put("properties", node.getOrDefault("properties", Map.of()));
                        grouped.put(type, item);
                    }
                }
                bom.addAll(grouped.values());
            }
        }

        return ResponseEntity.ok(ApiResponse.success("BOM generated", bom));
    }

    @GetMapping("/{projectId}/stats")
    @Operation(summary = "Get project statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProjectStats(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) {
        var project = projectService.getProject(projectId, jwt != null ? jwt.getSubject() : null);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("viewCount", project.getViewCount());
        stats.put("forkCount", project.getForkCount());
        stats.put("codeFiles", project.getCodeFiles() != null ? project.getCodeFiles().size() : 0);

        int componentCount = 0, wireCount = 0;
        if (project.getCanvasLayout() != null) {
            @SuppressWarnings("unchecked")
            var nodes = (List<?>) project.getCanvasLayout().get("nodes");
            @SuppressWarnings("unchecked")
            var wires = (List<?>) project.getCanvasLayout().get("wires");
            componentCount = nodes != null ? nodes.size() : 0;
            wireCount = wires != null ? wires.size() : 0;
        }
        stats.put("componentCount", componentCount);
        stats.put("wireCount", wireCount);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/{projectId}/export/zip")
    @Operation(summary = "Export project as ZIP file")
    public ResponseEntity<byte[]> exportZip(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {
        return exportProject(projectId, jwt);
    }

    @GetMapping("/{projectId}/export")
    @Operation(summary = "Export project source and diagram as ZIP file")
    public ResponseEntity<byte[]> exportProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {
        var project = projectService.getProject(projectId, jwt != null ? jwt.getSubject() : null);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            // Add README
            java.util.zip.ZipEntry readmeEntry = new java.util.zip.ZipEntry("README.md");
            zos.putNextEntry(readmeEntry);
            String readme = "# " + project.getName() + "\n\n" + (project.getDescription() != null ? project.getDescription() : "");
            zos.write(readme.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add Code Files
            if (project.getCodeFiles() != null) {
                for (var file : project.getCodeFiles()) {
                    zos.putNextEntry(new java.util.zip.ZipEntry("src/" + file.getFilename()));
                    zos.write(file.getContent() != null ? file.getContent().getBytes(StandardCharsets.UTF_8) : new byte[0]);
                    zos.closeEntry();
                }
            }

            Map<String, Object> diagram = new LinkedHashMap<>();
            diagram.put("projectId", project.getId());
            diagram.put("name", project.getName());
            diagram.put("boardType", project.getBoardType());
            diagram.put("canvasLayout", project.getCanvasLayout() != null ? project.getCanvasLayout() : Map.of("nodes", List.of(), "wires", List.of()));
            diagram.put("componentConfig", project.getComponentConfig() != null ? project.getComponentConfig() : Map.of());

            zos.putNextEntry(new java.util.zip.ZipEntry("diagram.json"));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(diagram));
            zos.closeEntry();
        }

        byte[] zipBytes = baos.toByteArray();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + project.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".zip\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/zip")
                .body(zipBytes);
    }
}
