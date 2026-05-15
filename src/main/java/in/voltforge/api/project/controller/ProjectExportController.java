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

    @GetMapping("/{projectId}/export/gerber")
    @Operation(summary = "Export PCB manufacturing Gerber bundle")
    public ResponseEntity<byte[]> exportGerber(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {
        var project = projectService.getProject(projectId, jwt != null ? jwt.getSubject() : null);
        Map<String, Object> layout = project.getCanvasLayout() != null ? project.getCanvasLayout() : Map.of();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) layout.getOrDefault("nodes", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wires = (List<Map<String, Object>>) layout.getOrDefault("wires", List.of());

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            writeZipEntry(zos, "README.txt", "VoltForge generated prototype Gerber set\nProject: " + project.getName() + "\n");
            writeZipEntry(zos, "board-F_Cu.gbr", buildCopperGerber(nodes, wires, false));
            writeZipEntry(zos, "board-B_Cu.gbr", buildCopperGerber(nodes, wires, true));
            writeZipEntry(zos, "board-Edge_Cuts.gbr", buildEdgeCuts(nodes));
            writeZipEntry(zos, "board.drl", buildExcellon(nodes));
            writeZipEntry(zos, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "projectId", project.getId(),
                    "format", "GERBER_RS274X_PROTOTYPE",
                    "layers", List.of("F_Cu", "B_Cu", "Edge_Cuts", "Drill"),
                    "traceCount", wires.size(),
                    "componentCount", nodes.size()
            )));
        }

        byte[] zipBytes = baos.toByteArray();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + project.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + "_gerber.zip\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/zip")
                .body(zipBytes);
    }

    private void writeZipEntry(java.util.zip.ZipOutputStream zos, String name, String content) throws java.io.IOException {
        zos.putNextEntry(new java.util.zip.ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String buildCopperGerber(List<Map<String, Object>> nodes, List<Map<String, Object>> wires, boolean bottomLayer) {
        StringBuilder gerber = new StringBuilder();
        gerber.append("G04 VoltForge ").append(bottomLayer ? "Bottom" : "Top").append(" Copper*\n");
        gerber.append("%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,0.450*%\nD10*\n");
        for (int i = 0; i < wires.size(); i++) {
            if ((i % 2 == 1) != bottomLayer) {
                continue;
            }
            Map<String, Object> wire = wires.get(i);
            Map<String, Object> start = pinPosition(nodes, String.valueOf(wire.get("fromNodeId")), String.valueOf(wire.get("fromPinId")));
            Map<String, Object> end = pinPosition(nodes, String.valueOf(wire.get("toNodeId")), String.valueOf(wire.get("toPinId")));
            if (start.isEmpty() || end.isEmpty()) {
                continue;
            }
            gerber.append(coord(start, "D02")).append("\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bends = (List<Map<String, Object>>) wire.getOrDefault("bendPoints", List.of());
            for (Map<String, Object> bend : bends) {
                gerber.append(coord(bend, "D01")).append("\n");
            }
            gerber.append(coord(end, "D01")).append("\n");
        }
        gerber.append("M02*\n");
        return gerber.toString();
    }

    private String buildEdgeCuts(List<Map<String, Object>> nodes) {
        double maxX = 100;
        double maxY = 80;
        for (Map<String, Object> node : nodes) {
            maxX = Math.max(maxX, number(node.get("x")) + number(node.get("width")) + 20);
            maxY = Math.max(maxY, number(node.get("y")) + number(node.get("height")) + 20);
        }
        return "G04 VoltForge Edge Cuts*\n%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,0.150*%\nD10*\n"
                + gerberCoord(0, 0, "D02") + "\n"
                + gerberCoord(maxX / 10, 0, "D01") + "\n"
                + gerberCoord(maxX / 10, maxY / 10, "D01") + "\n"
                + gerberCoord(0, maxY / 10, "D01") + "\n"
                + gerberCoord(0, 0, "D01") + "\nM02*\n";
    }

    private String buildExcellon(List<Map<String, Object>> nodes) {
        StringBuilder drill = new StringBuilder("M48\nMETRIC,TZ\nT1C0.800\n%\nT1\n");
        for (Map<String, Object> node : nodes) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pins = (List<Map<String, Object>>) node.getOrDefault("pins", List.of());
            for (Map<String, Object> pin : pins) {
                double x = (number(node.get("x")) + number(pin.get("x"))) / 10;
                double y = (number(node.get("y")) + number(pin.get("y"))) / 10;
                drill.append("X").append(format(x)).append("Y").append(format(y)).append("\n");
            }
        }
        drill.append("M30\n");
        return drill.toString();
    }

    private Map<String, Object> pinPosition(List<Map<String, Object>> nodes, String nodeId, String pinId) {
        for (Map<String, Object> node : nodes) {
            if (!nodeId.equals(String.valueOf(node.get("id")))) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pins = (List<Map<String, Object>>) node.getOrDefault("pins", List.of());
            for (Map<String, Object> pin : pins) {
                if (pinId.equals(String.valueOf(pin.get("id")))) {
                    return Map.of(
                            "x", number(node.get("x")) + number(pin.get("x")),
                            "y", number(node.get("y")) + number(pin.get("y"))
                    );
                }
            }
        }
        return Map.of();
    }

    private String coord(Map<String, Object> point, String op) {
        return gerberCoord(number(point.get("x")) / 10, number(point.get("y")) / 10, op);
    }

    private String gerberCoord(double x, double y, String op) {
        return "X" + format(x) + "Y" + format(y) + op + "*";
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%07.3f", value).replace(".", "");
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
