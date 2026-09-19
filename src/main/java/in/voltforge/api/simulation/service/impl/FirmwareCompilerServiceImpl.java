package in.voltforge.api.simulation.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import in.voltforge.api.common.enums.BoardType;
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
import java.util.LinkedHashMap;
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

    private static final Map<BoardType, CompilerTarget> COMPILER_TARGETS = Map.ofEntries(
            Map.entry(BoardType.ARDUINO_UNO, target("arduino:avr:uno", "uno")),
            Map.entry(BoardType.ATMEL_AVR_ATMEGA328P, target("arduino:avr:uno", "uno")),
            Map.entry(BoardType.ARDUINO_NANO, target("arduino:avr:nano", "nano")),
            Map.entry(BoardType.ARDUINO_MEGA, target("arduino:avr:mega", "mega")),
            Map.entry(BoardType.ARDUINO_LEONARDO, target("arduino:avr:leonardo")),
            Map.entry(BoardType.ARDUINO_MICRO, target("arduino:avr:micro")),
            Map.entry(BoardType.ARDUINO_NANO_EVERY, target("arduino:megaavr:nona4809")),
            Map.entry(BoardType.ARDUINO_NANO_33_IOT, target("arduino:samd:nano_33_iot")),
            Map.entry(BoardType.ARDUINO_DUE, target("arduino:sam:arduino_due_x_dbg")),
            Map.entry(BoardType.ARDUINO_UNO_R4, target("arduino:renesas_uno:unor4wifi")),
            Map.entry(BoardType.ARDUINO_GIGA_R1, target("arduino:mbed_giga:giga")),
            Map.entry(BoardType.ARDUINO_PORTENTA_H7, target("arduino:mbed_portenta:envie_m7")),

            Map.entry(BoardType.ESP8266, target("esp8266:esp8266:nodemcuv2", "esp8266")),
            Map.entry(BoardType.ESP8266_WEMOS_D1_MINI, target("esp8266:esp8266:d1_mini", "esp8266")),
            Map.entry(BoardType.ESP8266_ESP01, target("esp8266:esp8266:esp01", "esp8266")),
            Map.entry(BoardType.ESP8266_ESP12E, target("esp8266:esp8266:generic", "esp8266")),

            Map.entry(BoardType.ESP32, target("esp32:esp32:esp32", "esp32")),
            Map.entry(BoardType.ESP32_WROOM, target("esp32:esp32:esp32", "esp32")),
            Map.entry(BoardType.ESP32_WROVER, target("esp32:esp32:esp32wrover", "esp32")),
            Map.entry(BoardType.ESP32_S2, target("esp32:esp32:esp32s2", "esp32-s2")),
            Map.entry(BoardType.ESP32_S3, target("esp32:esp32:esp32s3", "esp32-s3")),
            Map.entry(BoardType.ESP32_C3, target("esp32:esp32:esp32c3", "esp32-c3")),
            Map.entry(BoardType.ESP32_C6, target("esp32:esp32:esp32c6", "esp32-c6")),
            Map.entry(BoardType.ESP32_H2, target("esp32:esp32:esp32h2", "esp32-h2")),
            Map.entry(BoardType.ESP32_TTGO, target("esp32:esp32:esp32", "esp32")),
            Map.entry(BoardType.ESP32_LILYGO, target("esp32:esp32:esp32", "esp32")),
            Map.entry(BoardType.ESP32_M5STACK, target("esp32:esp32:esp32", "esp32")),

            Map.entry(BoardType.RASPBERRY_PI_PICO, target("rp2040:rp2040:rpipico", "pi-pico")),
            Map.entry(BoardType.RASPBERRY_PI_PICO_W, target("rp2040:rp2040:rpipicow", "pi-pico-w")),
            Map.entry(BoardType.RASPBERRY_PI_PICO_2, target("rp2040:rp2040:rpipico2")),

            Map.entry(BoardType.STM32_BLUE_PILL, target("STMicroelectronics:stm32:GenF1:pnum=BLUEPILL_F103C8")),
            Map.entry(BoardType.STM32_BLACK_PILL, target("STMicroelectronics:stm32:GenF4:pnum=BLACKPILL_F411CE")),

            Map.entry(BoardType.TEENSY_4_0, target("teensy:avr:teensy40")),
            Map.entry(BoardType.TEENSY_4_1, target("teensy:avr:teensy41")),
            Map.entry(BoardType.TEENSY_LC, target("teensy:avr:teensyLC")),

            Map.entry(BoardType.SEEED_XIAO_SAMD21, target("Seeeduino:samd:seeed_XIAO_m0")),
            Map.entry(BoardType.SEEED_XIAO_RP2040, target("rp2040:rp2040:seeed_xiao_rp2040")),
            Map.entry(BoardType.SEEED_XIAO_ESP32C3, target("esp32:esp32:XIAO_ESP32C3", "esp32-c3")),
            Map.entry(BoardType.SEEED_XIAO_ESP32S3, target("esp32:esp32:XIAO_ESP32S3", "esp32-s3")),

            Map.entry(BoardType.ADAFRUIT_FEATHER_M0, target("adafruit:samd:adafruit_feather_m0")),
            Map.entry(BoardType.ADAFRUIT_FEATHER_M4, target("adafruit:samd:adafruit_feather_m4")),
            Map.entry(BoardType.ADAFRUIT_FEATHER_ESP32, target("esp32:esp32:featheresp32", "esp32")),
            Map.entry(BoardType.ADAFRUIT_FEATHER_RP2040, target("rp2040:rp2040:adafruit_feather")),
            Map.entry(BoardType.ADAFRUIT_FEATHER_NRF52840, target("adafruit:nrf52:feather52840")),

            Map.entry(BoardType.SPARKFUN_THING_PLUS_ESP32, target("esp32:esp32:esp32", "esp32")),
            Map.entry(BoardType.SPARKFUN_THING_PLUS_RP2040, target("rp2040:rp2040:rpipico")),
            Map.entry(BoardType.SPARKFUN_THING_PLUS_ARTEMIS, target("SparkFun:apollo3:sfe_artemis_thing_plus")),

            Map.entry(BoardType.SEEED_XIAO_NRF52840, target("Seeeduino:nrf52:xiaonRF52840")),

            Map.entry(BoardType.ATMEL_AVR_ATTINY, target("ATTinyCore:avr:attinyx5")));

    private final JsonMapper objectMapper;
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
        CompilerTarget target = COMPILER_TARGETS.get(boardType);
        String fqbn = target == null ? "unsupported:" + boardType.name() : target.fqbn();
        String mode = compilerMode == null ? "REMOTE" : compilerMode.trim().toUpperCase(Locale.ROOT);

        if ("ARDUINO_CLI".equals(mode) || "LOCAL".equals(mode)) {
            if (target == null) {
                return unsupported(boardType, fqbn, "ARDUINO_CLI:" + arduinoCliPath,
                        unsupportedMessage(boardType, "local Arduino CLI"));
            }
            return compileWithArduinoCli(request, boardType, target);
        }

        if (target == null || target.remoteBoard() == null) {
            return unsupported(boardType, fqbn, "REMOTE:" + remoteCompilerUrl,
                    unsupportedMessage(boardType, "remote compiler"));
        }
        return compileWithRemoteService(request, boardType, target);
    }

    private FirmwareCompileResponse compileWithRemoteService(FirmwareCompileRequest request, BoardType boardType,
            CompilerTarget target) {
        try {
            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(remoteCompilerUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "sketch", request.getSource(),
                            "board", target.remoteBoard()))
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
                    .fqbn(target.fqbn())
                    .compiler("REMOTE:" + remoteCompilerUrl)
                    .hex(hex)
                    .stdout(stdout)
                    .stderr(stderr)
                    .diagnostics(toDiagnostics(stdout, stderr))
                    .metadata(Map.of("mode", "REMOTE", "remoteBoard", target.remoteBoard()))
                    .build();
        } catch (WebClientResponseException e) {
            log.warn("Remote compiler returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return failed(boardType, target.fqbn(), "REMOTE:" + remoteCompilerUrl, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Remote firmware compilation failed: {}", e.toString());
            return failed(boardType, target.fqbn(), "REMOTE:" + remoteCompilerUrl, e.getMessage());
        }
    }

    private FirmwareCompileResponse compileWithArduinoCli(FirmwareCompileRequest request, BoardType boardType,
            CompilerTarget target) {
        Path workDir = null;
        try {
            String sketchName = sanitizeSketchName(request.getSketchName());
            workDir = Files.createTempDirectory("voltforge-fw-" + UUID.randomUUID());
            Path sketchDir = workDir.resolve(sketchName);
            Path outputDir = workDir.resolve("build");
            Files.createDirectories(sketchDir);
            Files.createDirectories(outputDir);
            Files.writeString(sketchDir.resolve(sketchName + ".ino"), request.getSource(), StandardCharsets.UTF_8);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    arduinoCliPath,
                    "compile",
                    "--fqbn",
                    target.fqbn(),
                    "--output-dir",
                    outputDir.toString(),
                    sketchDir.toString());
            processBuilder.directory(workDir.toFile());
            Process process = processBuilder.start();
            CompletableFuture<String> stdoutFuture = readAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readAsync(process.getErrorStream());

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return failed(boardType, target.fqbn(), "ARDUINO_CLI:" + arduinoCliPath,
                        "Compiler timed out after " + timeoutSeconds + " seconds");
            }

            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            CompiledArtifact artifact = process.exitValue() == 0 ? findCompiledArtifact(outputDir) : null;
            String hex = artifact == null ? "" : artifact.hex();

            return FirmwareCompileResponse.builder()
                    .success(process.exitValue() == 0 && artifact != null)
                    .boardType(boardType.name())
                    .fqbn(target.fqbn())
                    .compiler("ARDUINO_CLI:" + arduinoCliPath)
                    .hex(hex)
                    .stdout(stdout)
                    .stderr(stderr)
                    .diagnostics(toDiagnostics(stdout, stderr))
                    .metadata(artifactMetadata(process.exitValue(), artifact))
                    .build();
        } catch (Exception e) {
            log.warn("Local firmware compilation failed: {}", e.toString());
            return failed(boardType, target.fqbn(), "ARDUINO_CLI:" + arduinoCliPath, e.getMessage());
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private CompiledArtifact findCompiledArtifact(Path outputDir) throws IOException {
        try (Stream<Path> files = Files.walk(outputDir)) {
            Path artifact = files
                    .filter(Files::isRegularFile)
                    .filter(path -> isFirmwareArtifact(path.getFileName().toString()))
                    .sorted(Comparator.comparingInt(path -> artifactPriority(path.getFileName().toString())))
                    .findFirst()
                    .orElse(null);
            if (artifact == null) {
                return null;
            }
            String fileName = artifact.getFileName().toString();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String hex = "hex".equals(extension) ? Files.readString(artifact, StandardCharsets.UTF_8) : "";
            return new CompiledArtifact(hex, extension, Files.size(artifact));
        }
    }

    private boolean isFirmwareArtifact(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".hex") || lower.endsWith(".bin") || lower.endsWith(".uf2") || lower.endsWith(".elf");
    }

    private int artifactPriority(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".hex")) return 0;
        if (lower.endsWith(".bin")) return 1;
        if (lower.endsWith(".uf2")) return 2;
        if (lower.endsWith(".elf")) return 3;
        return 4;
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
        return node == null || node.isNull() ? "" : node.asString("");
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

    private FirmwareCompileResponse unsupported(BoardType boardType, String fqbn, String compiler, String message) {
        return FirmwareCompileResponse.builder()
                .success(false)
                .boardType(boardType.name())
                .fqbn(fqbn)
                .compiler(compiler)
                .hex("")
                .stdout("")
                .stderr(message)
                .diagnostics(List.of(message))
                .metadata(Map.of("mode", compiler, "unsupported", true))
                .build();
    }

    private String unsupportedMessage(BoardType boardType, String compilerLabel) {
        if (isLinuxBoard(boardType)) {
            return boardType.name()
                    + " is a Linux SBC target. VoltForge supports it on the canvas and GPIO planner, "
                    + "but firmware compilation for Linux applications must run in that board's native toolchain.";
        }
        if (COMPILER_TARGETS.containsKey(boardType)) {
            return boardType.name()
                    + " has a local Arduino CLI FQBN, but it is not available through the configured "
                    + compilerLabel
                    + ". Switch app.simulation.compiler.mode to LOCAL/ARDUINO_CLI and install the matching board core.";
        }
        return boardType.name()
                + " is available as a canvas/GPIO component, but no safe firmware compiler target is configured for "
                + compilerLabel
                + ". Add an explicit FQBN mapping before enabling compilation for this board.";
    }

    private boolean isLinuxBoard(BoardType boardType) {
        String name = boardType.name();
        return name.startsWith("RASPBERRY_PI_") && !name.startsWith("RASPBERRY_PI_PICO")
                || name.startsWith("BEAGLEBONE")
                || name.startsWith("ODROID")
                || name.startsWith("ORANGE_PI")
                || name.startsWith("JETSON")
                || name.startsWith("CORAL")
                || name.equals("RISC_V_VISIONFIVE")
                || name.equals("RENESAS_RZ");
    }

    private Map<String, Object> artifactMetadata(int exitCode, CompiledArtifact artifact) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exitCode", exitCode);
        metadata.put("mode", "ARDUINO_CLI");
        if (artifact != null) {
            metadata.put("artifactExtension", artifact.extension());
            metadata.put("artifactSizeBytes", artifact.sizeBytes());
        }
        return metadata;
    }

    private static CompilerTarget target(String fqbn) {
        return new CompilerTarget(fqbn, null);
    }

    private static CompilerTarget target(String fqbn, String remoteBoard) {
        return new CompilerTarget(fqbn, remoteBoard);
    }

    private record CompilerTarget(String fqbn, String remoteBoard) {
    }

    private record CompiledArtifact(String hex, String extension, long sizeBytes) {
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
