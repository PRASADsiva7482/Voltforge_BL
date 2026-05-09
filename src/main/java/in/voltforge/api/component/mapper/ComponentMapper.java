package in.voltforge.api.component.mapper;

import in.voltforge.api.component.dto.ComponentResponse;
import in.voltforge.api.component.entity.ElectronicComponent;
import org.springframework.stereotype.Component;

@Component
public class ComponentMapper {

    public ComponentResponse toResponse(ElectronicComponent component) {
        if (component == null) return null;
        return ComponentResponse.builder()
                .id(component.getId())
                .name(component.getName())
                .category(component.getCategory())
                .type(component.getType())
                .description(component.getDescription())
                .defaultProperties(component.getDefaultProperties())
                .pinConfig(component.getPinConfig())
                .iconUrl(component.getIconUrl())
                .svgData(component.getSvgData())
                .isPremium(component.getIsPremium())
                .sortOrder(component.getSortOrder())
                .createdAt(component.getCreatedAt())
                .build();
    }
}
