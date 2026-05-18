package in.voltforge.api.simulation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.common.exception.BadRequestException;
import in.voltforge.api.simulation.dto.FirmwareCompileRequest;
import in.voltforge.api.simulation.dto.FirmwareCompileResponse;
import in.voltforge.api.simulation.service.FirmwareCompilerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareCompilerServiceImpl implements FirmwareCompilerService {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.simulation.compiler.mode:REMOTE}")
    private String compilerMode;

    @Value("${app.simulation.compiler.remote-url:https://hexi.wokwi.com/build}")
    private String remoteCompilerUrl;

    @Value("${app.simulation.compiler.arduino-cli-path:arduino-cli}")
    private String arduinoCliPath;

    @Value("${app.simulation.compiler.timeout-seconds:45}")
    private int timeoutSeconds;

    @Override
    public FirmwareCompileResponse compile(FirmwareCompileRequest request) {
        BoardType boardType = request.getBoardType() != null ? request.getBoardType() : BoardType.ARDUINO_UNO;
        String fqbn = fqbnFor(boardType);
        String mode = compilerMode == null ? "REMOTE" : compilerMode.trim().toUpperCase(Locale.ROOT);

        if ("ARDUINO_CLI".equals(mode) || "LOCAL".equals(mode)) {
            return compileWithArduinoCli(request, boardType, fqbn);
        }

        return compileWithRemoteService(request, boardType, fqbn);
    }

    private FirmwareCompileResponse compileWithRemoteService(FirmwareCompileRequest request, BoardType boardType,
            String fqbn) {
        try {
            // Map BoardType to the short board name expected by Wokwi Hexi API
            String wokwiBoard = switch (boardType) {
                case ARDUINO_MEGA -> "mega";
                case ARDUINO_NANO -> "nano";
                default -> "uno";
            };

            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(remoteCompilerUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "sketch", request.getSource(),
                            "board", wokwiBoard))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = responseBody == null ? objectMapper.createObjectNode()
                    : objectMapper.readTree(responseBody);
            String hex = textOrEmpty(root, "hex");
            String stdout = textOrEmpty(root, "stdout");
            String stderr = textOrEmpty(root, "stderr");

            return FirmwareCompileResponse.builder()
                    .success(!hex.isBlank())
                    .boardType(boardType.name())
                    .fqbn(fqbn)
                    .compiler("REMOTE:" + remoteCompilerUrl)
                    .hex(hex)
                    .stdout(stdout)
                    .stderr(stderr)
                    .diagnostics(toDiagnostics(stdout, stderr))
                    .metadata(Map.of("mode", "REMOTE"))
                    .build();
        } catch (WebClientResponseException e) {
            log.warn("Remote compiler returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return failed(boardType, fqbn, "REMOTE:" + remoteCompilerUrl, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Remote firmware compilation failed: {}", e.getMessage(), e);
            return failed(boardType, fqbn, "REMOTE:" + remoteCompilerUrl, e.getMessage());
        }
    }

    private FirmwareCompileResponse compileWithArduinoCli(FirmwareCompileRequest request, BoardType boardType,
            String fqbn) {
        Path workDir = null;
        try {
            String sketchName = sanitizeSketchName(request.getSketchName());
            workDir = Files.createTempDirectory("voltforge-avr-" + UUID.randomUUID());
            Path sketchDir = workDir.resolve(sketchName);
            Path outputDir = workDir.resolve("build");
            Files.createDirectories(sketchDir);
            Files.createDirectories(outputDir);
            Files.writeString(sketchDir.resolve(sketchName + ".ino"), request.getSource(), StandardCharsets.UTF_8);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    arduinoCliPath,
                    "compile",
                    "--fqbn",
                    fqbn,
                    "--output-dir",
                    outputDir.toString(),
                    sketchDir.toString());
            Process process = processBuilder.start();
            CompletableFuture<String> stdoutFuture = readAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readAsync(process.getErrorStream());

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return failed(boardType, fqbn, "ARDUINO_CLI:" + arduinoCliPath,
                        "Compiler timed out after " + timeoutSeconds + " seconds");
            }

            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            String hex = process.exitValue() == 0 ? findHex(outputDir) : "";

            return FirmwareCompileResponse.builder()
                    .success(process.exitValue() == 0 && !hex.isBlank())
                    .boardType(boardType.name())
                    .fqbn(fqbn)
                    .compiler("ARDUINO_CLI:" + arduinoCliPath)
                    .hex(hex)
                    .stdout(stdout)
                    .stderr(stderr)
                    .diagnostics(toDiagnostics(stdout, stderr))
                    .metadata(Map.of("exitCode", process.exitValue(), "mode", "ARDUINO_CLI"))
                    .build();
        } catch (Exception e) {
            log.warn("Local firmware compilation failed: {}", e.getMessage(), e);
            return failed(boardType, fqbn, "ARDUINO_CLI:" + arduinoCliPath, e.getMessage());
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private String fqbnFor(BoardType boardType) {
        return switch (boardType) {
            case ARDUINO_UNO -> "arduino:avr:uno";
            case ARDUINO_NANO -> "arduino:avr:nano";
            case ARDUINO_MEGA -> "arduino:avr:mega";
            case ESP32, ESP32_S3, ESP8266 ->
                throw new BadRequestException("AVR simulation currently supports Arduino Uno, Nano, and Mega boards");
        };
    }

    private String findHex(Path outputDir) throws IOException {
        try (Stream<Path> files = Files.walk(outputDir)) {
            Path hexFile = files
                    .filter(path -> path.getFileName().toString().endsWith(".hex"))
                    .findFirst()
                    .orElse(null);
            return hexFile == null ? "" : Files.readString(hexFile, StandardCharsets.UTF_8);
        }
    }

    private CompletableFuture<String> readAsync(java.io.InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return e.getMessage();
            }
        });
    }

    private String textOrEmpty(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private List<String> toDiagnostics(String stdout, String stderr) {
        List<String> diagnostics = new ArrayList<>();
        addDiagnosticLines(diagnostics, stderr);
        addDiagnosticLines(diagnostics, stdout);
        return diagnostics.stream().limit(50).toList();
    }

    private void addDiagnosticLines(List<String> diagnostics, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()
                    && (trimmed.toLowerCase(Locale.ROOT).contains("error")
                            || trimmed.toLowerCase(Locale.ROOT).contains("warning")
                            || trimmed.contains(".ino:"))) {
                diagnostics.add(trimmed);
            }
        }
    }

    private FirmwareCompileResponse failed(BoardType boardType, String fqbn, String compiler, String error) {
        return FirmwareCompileResponse.builder()
                .success(false)
                .boardType(boardType.name())
                .fqbn(fqbn)
                .compiler(compiler)
                .hex("")
                .stdout("")
                .stderr(error == null ? "Compilation failed" : error)
                .diagnostics(error == null ? List.of("Compilation failed") : List.of(error))
                .metadata(Map.of("mode", compiler))
                .build();
    }

    private String sanitizeSketchName(String sketchName) {
        String value = sketchName == null || sketchName.isBlank() ? "VoltForgeSketch" : sketchName;
        value = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (!Character.isLetter(value.charAt(0))) {
            value = "Sketch_" + value;
        }
        return value.length() > 48 ? value.substring(0, 48) : value;
    }

    private void deleteQuietly(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    log.debug("Failed to delete compiler temp path {}", path);
                }
            });
        } catch (IOException ignored) {
            log.debug("Failed to clean compiler temp directory {}", root);
        }
    }
}
