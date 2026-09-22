package in.voltforge.api.simulation.service.impl;

import tools.jackson.databind.json.JsonMapper;
import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.simulation.dto.FirmwareCompileRequest;
import in.voltforge.api.simulation.dto.FirmwareCompileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class FirmwareCompilerServiceImplTest {

    @Test
    void remoteCompileParsesHexResponse() {
        FirmwareCompilerServiceImpl service = serviceWithRemoteResponse(
                "{\"hex\":\":100000000C945C000C946E000C946E000C946E00CA\",\"stdout\":\"ok\",\"stderr\":\"\"}");
        ReflectionTestUtils.setField(service, "compilerMode", "REMOTE");
        ReflectionTestUtils.setField(service, "remoteCompilerUrl", "https://compiler.example/build");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 5);

        FirmwareCompileResponse response = service.compile(FirmwareCompileRequest.builder()
                .source("void setup(){} void loop(){}")
                .boardType(BoardType.ARDUINO_UNO)
                .sketchName("Motor Test")
                .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getFqbn()).isEqualTo("arduino:avr:uno");
        assertThat(response.getHex()).startsWith(":10000000");
        assertThat(response.getCompiler()).isEqualTo("REMOTE:https://compiler.example/build");
    }

    @Test
    void remoteCompileUsesExpandedBoardTarget() {
        FirmwareCompilerServiceImpl service = serviceWithRemoteResponse(
                "{\"hex\":\":100000000C945C000C946E000C946E000C946E00CA\",\"stdout\":\"ok\",\"stderr\":\"\"}");
        ReflectionTestUtils.setField(service, "compilerMode", "REMOTE");
        ReflectionTestUtils.setField(service, "remoteCompilerUrl", "https://compiler.example/build");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 5);

        FirmwareCompileResponse response = service.compile(FirmwareCompileRequest.builder()
                .source("void setup(){} void loop(){}")
                .boardType(BoardType.ESP32)
                .sketchName("ESP32 Test")
                .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getFqbn()).isEqualTo("esp32:esp32:esp32");
        assertThat(response.getMetadata()).containsEntry("remoteBoard", "esp32");
    }

    @Test
    void unsupportedBoardReturnsStructuredFailure() {
        FirmwareCompilerServiceImpl service = serviceWithRemoteResponse("{}");

        FirmwareCompileResponse response = service.compile(FirmwareCompileRequest.builder()
                .source("void setup(){}")
                .boardType(BoardType.BEAGLEBONE_BLACK)
                .build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getFqbn()).isEqualTo("unsupported:BEAGLEBONE_BLACK");
        assertThat(response.getStderr()).contains("Linux SBC");
        assertThat(response.getMetadata()).containsEntry("unsupported", true);
    }

    @Test
    void localCompilerFailureReturnsStructuredResponse() {
        FirmwareCompilerServiceImpl service = serviceWithRemoteResponse("{}");
        ReflectionTestUtils.setField(service, "compilerMode", "LOCAL");
        ReflectionTestUtils.setField(service, "arduinoCliPath", "definitely-not-a-real-arduino-cli");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 1);

        FirmwareCompileResponse response = service.compile(FirmwareCompileRequest.builder()
                .source("void setup(){}")
                .boardType(BoardType.ARDUINO_NANO)
                .sketchName("123 unsafe sketch name")
                .build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getFqbn()).isEqualTo("arduino:avr:nano");
        assertThat(response.getCompiler()).contains("ARDUINO_CLI");
        assertThat(response.getDiagnostics()).isNotEmpty();
    }

    private FirmwareCompilerServiceImpl serviceWithRemoteResponse(String body) {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build()));
        return new FirmwareCompilerServiceImpl(new JsonMapper(), builder);
    }
}
