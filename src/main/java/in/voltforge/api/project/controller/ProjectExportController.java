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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
                    String filename = safeArchiveFilename(file.getFilename());
                    zos.putNextEntry(new java.util.zip.ZipEntry("src/" + filename));
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

        Map<String, Object> componentConfig = project.getComponentConfig() != null
                ? project.getComponentConfig() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> pcbLayout = componentConfig.get("pcbLayout") instanceof Map
                ? (Map<String, Object>) componentConfig.get("pcbLayout") : Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pcbTraces = pcbLayout.get("traces") instanceof List
                ? (List<Map<String, Object>>) pcbLayout.get("traces") : List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pcbFootprints = pcbLayout.get("footprints") instanceof List
                ? (List<Map<String, Object>>) pcbLayout.get("footprints") : List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pcbVias = pcbLayout.get("vias") instanceof List
                ? (List<Map<String, Object>>) pcbLayout.get("vias") : List.of();
        boolean hasPersistedPcb = !pcbTraces.isEmpty() || !pcbFootprints.isEmpty() || !pcbVias.isEmpty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) layout.getOrDefault("nodes", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wires = (List<Map<String, Object>>) layout.getOrDefault("wires", List.of());

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            writeZipEntry(zos, "README.txt", "VoltForge generated Gerber set\n"
                    + "Wires without an explicit layer are placed on F_Cu.\nProject: " + project.getName() + "\n");
            writeZipEntry(zos, "board-F_Cu.gbr", hasPersistedPcb
                    ? buildPcbCopperGerber(pcbTraces, pcbFootprints, false)
                    : buildCopperGerber(nodes, wires, false));
            writeZipEntry(zos, "board-B_Cu.gbr", hasPersistedPcb
                    ? buildPcbCopperGerber(pcbTraces, pcbFootprints, true)
                    : buildCopperGerber(nodes, wires, true));
            writeZipEntry(zos, "board-Edge_Cuts.gbr", hasPersistedPcb
                    ? buildPcbEdgeCuts(pcbLayout)
                    : buildEdgeCuts(nodes));
            writeZipEntry(zos, "board.drl", hasPersistedPcb
                    ? buildPcbExcellon(pcbFootprints, pcbVias)
                    : buildExcellon(nodes));
            writeZipEntry(zos, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "projectId", project.getId(),
                    "format", hasPersistedPcb ? "GERBER_RS274X_PCB_LAYOUT" : "GERBER_RS274X_SCHEMATIC_FALLBACK",
                    "layers", List.of("F_Cu", "B_Cu", "Edge_Cuts", "Drill"),
                    "traceCount", hasPersistedPcb ? pcbTraces.size() : wires.size(),
                    "componentCount", hasPersistedPcb ? pcbFootprints.size() : nodes.size(),
                    "viaCount", pcbVias.size()
            )));
        }

        byte[] zipBytes = baos.toByteArray();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + project.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + "_gerber.zip\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/zip")
                .body(zipBytes);
    }

    private String safeArchiveFilename(String filename) {
        if (filename == null || !filename.matches("^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$")
                || filename.equals(".") || filename.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project contains an invalid code filename");
        }
        return filename;
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
        for (Map<String, Object> wire : wires) {
            if (wireIsOnBottomLayer(wire) != bottomLayer) {
                continue;
            }
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

    private String buildPcbCopperGerber(
            List<Map<String, Object>> traces,
            List<Map<String, Object>> footprints,
            boolean bottomLayer) {
        StringBuilder gerber = new StringBuilder();
        gerber.append("G04 VoltForge persisted PCB ")
                .append(bottomLayer ? "Bottom" : "Top").append(" Copper*\n")
                .append("%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,0.250*%\n");

        // Footprint pads are part of the copper artwork too. Define one
        // aperture per pad geometry so SMD/THT pads are not silently omitted
        // from the manufacturing export.
        Map<String, Integer> padApertures = new LinkedHashMap<>();
        int nextAperture = 11;
        for (Map<String, Object> footprint : footprints) {
            if (bottomLayer) continue; // persisted footprints are front-side by default
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pads = footprint.get("pads") instanceof List
                    ? (List<Map<String, Object>>) footprint.get("pads") : List.of();
            for (Map<String, Object> pad : pads) {
                String geometry = padGeometry(pad);
                if (!padApertures.containsKey(geometry)) {
                    padApertures.put(geometry, nextAperture++);
                }
            }
        }
        for (Map.Entry<String, Integer> aperture : padApertures.entrySet()) {
            gerber.append("%ADD").append(aperture.getValue()).append(aperture.getKey()).append("*%\n");
        }
        gerber.append("D10*\n");
        for (Map<String, Object> trace : traces) {
            String layer = String.valueOf(trace.getOrDefault("layer", "F.Cu"));
            boolean isBottom = "B.Cu".equalsIgnoreCase(layer) || "B_Cu".equalsIgnoreCase(layer)
                    || "bottom".equalsIgnoreCase(layer);
            if (isBottom != bottomLayer) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> points = trace.get("points") instanceof List
                    ? (List<Map<String, Object>>) trace.get("points") : List.of();
            if (points.size() < 2) continue;
            gerber.append(pcbCoord(points.get(0), "D02")).append("\n");
            for (int i = 1; i < points.size(); i++) {
                gerber.append(pcbCoord(points.get(i), "D01")).append("\n");
            }
        }

        if (!bottomLayer) {
            for (Map<String, Object> footprint : footprints) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pads = footprint.get("pads") instanceof List
                        ? (List<Map<String, Object>>) footprint.get("pads") : List.of();
                double fx = number(footprint.get("x"));
                double fy = number(footprint.get("y"));
                double rotation = Math.toRadians(number(footprint.get("rotation")));
                for (Map<String, Object> pad : pads) {
                    Integer aperture = padApertures.get(padGeometry(pad));
                    if (aperture == null) continue;
                    double localX = number(pad.get("x"));
                    double localY = number(pad.get("y"));
                    Map<String, Object> point = Map.of(
                            "x", fx + localX * Math.cos(rotation) - localY * Math.sin(rotation),
                            "y", fy + localX * Math.sin(rotation) + localY * Math.cos(rotation)
                    );
                    gerber.append("D").append(aperture).append("*\n")
                            .append(pcbCoord(point, "D03")).append("\n");
                }
            }
        }
        gerber.append("M02*\n");
        return gerber.toString();
    }

    private String padGeometry(Map<String, Object> pad) {
        double width = Math.max(0.05, number(pad.get("width")));
        double height = Math.max(0.05, number(pad.get("height")));
        String shape = String.valueOf(pad.getOrDefault("shape", "rect")).toLowerCase(Locale.ROOT);
        if ("circle".equals(shape)) {
            return String.format(Locale.ROOT, "C,%.3f", Math.max(width, height));
        }
        if ("oval".equals(shape)) {
            return String.format(Locale.ROOT, "O,%.3fX%.3f", width, height);
        }
        return String.format(Locale.ROOT, "R,%.3fX%.3f", width, height);
    }

    private String buildPcbEdgeCuts(Map<String, Object> pcbLayout) {
        double width = number(pcbLayout.getOrDefault("boardWidth_mm", 100));
        double height = number(pcbLayout.getOrDefault("boardHeight_mm", 80));
        return "G04 VoltForge persisted PCB edge cuts*\n%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,0.150*%\nD10*\n"
                + pcbCoord(Map.of("x", 0, "y", 0), "D02") + "\n"
                + pcbCoord(Map.of("x", width, "y", 0), "D01") + "\n"
                + pcbCoord(Map.of("x", width, "y", height), "D01") + "\n"
                + pcbCoord(Map.of("x", 0, "y", height), "D01") + "\n"
                + pcbCoord(Map.of("x", 0, "y", 0), "D01") + "\nM02*\n";
    }

    private String buildPcbExcellon(List<Map<String, Object>> footprints, List<Map<String, Object>> vias) {
        StringBuilder drill = new StringBuilder("M48\nMETRIC,TZ\nT1C0.800\nT2C0.300\n%\n");
        for (Map<String, Object> footprint : footprints) {
            double fx = number(footprint.get("x"));
            double fy = number(footprint.get("y"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pads = footprint.get("pads") instanceof List
                    ? (List<Map<String, Object>>) footprint.get("pads") : List.of();
            for (Map<String, Object> pad : pads) {
                if (pad.get("drillDiameter") == null) continue;
                double rotation = Math.toRadians(number(footprint.get("rotation")));
                double localX = number(pad.get("x"));
                double localY = number(pad.get("y"));
                double padX = fx + localX * Math.cos(rotation) - localY * Math.sin(rotation);
                double padY = fy + localX * Math.sin(rotation) + localY * Math.cos(rotation);
                drill.append("T1\nX").append(format(padX))
                        .append("Y").append(format(padY)).append("\n");
            }
        }
        for (Map<String, Object> via : vias) {
            drill.append("T2\nX").append(format(number(via.get("x"))))
                    .append("Y").append(format(number(via.get("y")))).append("\n");
        }
        drill.append("M30\n");
        return drill.toString();
    }

    private boolean wireIsOnBottomLayer(Map<String, Object> wire) {
        Object layer = wire.get("layer");
        if (layer == null) {
            return false;
        }
        String normalized = String.valueOf(layer).trim().toLowerCase(Locale.ROOT);
        return normalized.equals("b_cu") || normalized.equals("bottom") || normalized.equals("b");
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

    private String pcbCoord(Map<String, Object> point, String op) {
        return gerberCoord(number(point.get("x")), number(point.get("y")), op);
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
