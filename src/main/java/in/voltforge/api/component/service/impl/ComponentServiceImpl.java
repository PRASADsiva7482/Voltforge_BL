package in.voltforge.api.component.service.impl;

import in.voltforge.api.common.exception.ResourceNotFoundException;
import in.voltforge.api.component.dto.ComponentResponse;
import in.voltforge.api.component.dto.CustomComponentRequest;
import in.voltforge.api.component.entity.ElectronicComponent;
import in.voltforge.api.component.mapper.ComponentMapper;
import in.voltforge.api.component.repository.ComponentRepository;
import in.voltforge.api.component.service.ComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentServiceImpl implements ComponentService {

    private final ComponentRepository componentRepository;
    private final ComponentMapper componentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ComponentResponse> getAllComponents() {
        return componentRepository.findAll().stream()
                .map(componentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentResponse> getComponentsByCategory(String category) {
        return componentRepository.findByCategoryOrderBySortOrderAsc(category.toUpperCase()).stream()
                .map(componentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentResponse> getFreeComponents() {
        return componentRepository.findByIsPremiumFalseOrderBySortOrderAsc().stream()
                .map(componentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentResponse> searchComponents(String query) {
        return componentRepository.searchComponents(query).stream()
                .map(componentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ComponentResponse getComponentById(String id) {
        ElectronicComponent component = componentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Component", "id", id));
        return componentMapper.toResponse(component);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        return componentRepository.findAllCategories();
    }

    @Override
    @Transactional
    public ComponentResponse createCustomComponent(CustomComponentRequest request) {
        String type = request.getType();
        if (type == null || type.isBlank()) {
            type = "CUSTOM_" + request.getName().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        }

        Map<String, Object> defaultProperties = new LinkedHashMap<>();
        defaultProperties.put("width", request.getWidth() != null ? request.getWidth() : 120);
        defaultProperties.put("height", request.getHeight() != null ? request.getHeight() : 90);
        defaultProperties.put("custom", true);
        defaultProperties.put("community", Boolean.TRUE.equals(request.getPublishToCommunity()));

        Map<String, Object> pinConfig = new LinkedHashMap<>();
        pinConfig.put("pins", request.getPins());

        ElectronicComponent saved = componentRepository.save(ElectronicComponent.builder()
                .name(request.getName())
                .category(request.getCategory() == null ? "SENSOR" : request.getCategory().toUpperCase(Locale.ROOT))
                .type(type)
                .description(request.getDescription())
                .defaultProperties(defaultProperties)
                .pinConfig(pinConfig)
                .svgData(request.getSvgData())
                .isPremium(false)
                .sortOrder(10_000)
                .build());

        return componentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentResponse> getCommunityComponents() {
        return componentRepository.findByTypeStartingWithOrderBySortOrderAsc("CUSTOM_").stream()
                .filter(component -> {
                    Map<String, Object> props = component.getDefaultProperties();
                    return props != null && Boolean.TRUE.equals(props.get("community"));
                })
                .map(componentMapper::toResponse)
                .collect(Collectors.toList());
    }
}
