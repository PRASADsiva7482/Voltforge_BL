package in.voltforge.api.simulation.controller;

import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.simulation.dto.FirmwareCompileRequest;
import in.voltforge.api.simulation.dto.FirmwareCompileResponse;
import in.voltforge.api.simulation.service.FirmwareCompilerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "Firmware compilation and hardware simulation APIs")
public class SimulationController {

    private final FirmwareCompilerService firmwareCompilerService;

    @PostMapping("/compile")
    @Operation(summary = "Compile firmware for supported board targets")
    public ResponseEntity<ApiResponse<FirmwareCompileResponse>> compileFirmware(
            @Valid @RequestBody FirmwareCompileRequest request) {
        FirmwareCompileResponse response = firmwareCompilerService.compile(request);
        String message = response.isSuccess() ? "Firmware compiled" : "Firmware compilation failed";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }
}
