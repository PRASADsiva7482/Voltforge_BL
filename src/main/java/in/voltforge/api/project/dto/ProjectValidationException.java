package in.voltforge.api.project.dto;

import java.util.Map;

public class ProjectValidationException extends RuntimeException {
    private final Map<String, String> fields;
    public ProjectValidationException(String field, String message) {
        super(message);
        fields = Map.of(field, message);
    }
    public Map<String, String> getFields() { return fields; }
}
