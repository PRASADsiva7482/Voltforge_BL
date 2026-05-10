package in.voltforge.api.simulation.service;

import in.voltforge.api.simulation.dto.FirmwareCompileRequest;
import in.voltforge.api.simulation.dto.FirmwareCompileResponse;

public interface FirmwareCompilerService {

    FirmwareCompileResponse compile(FirmwareCompileRequest request);
}
