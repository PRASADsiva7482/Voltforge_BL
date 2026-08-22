package in.voltforge.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PcbManufacturingRequest {

    @JsonProperty("boardWidth_mm")
    private Double boardWidthMm;

    @JsonProperty("boardHeight_mm")
    private Double boardHeightMm;

    private List<Map<String, Object>> footprints;

    private List<Map<String, Object>> traces;

    private List<Map<String, Object>> vias;

    private List<Map<String, Object>> wires;

    private String projectName;
}
