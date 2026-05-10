package in.voltforge.api.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmwareCompileResponse {

    private boolean success;
    private String boardType;
    private String fqbn;
    private String compiler;
    private String hex;
    private String stdout;
    private String stderr;
    private List<String> diagnostics;
    private Map<String, Object> metadata;
}
