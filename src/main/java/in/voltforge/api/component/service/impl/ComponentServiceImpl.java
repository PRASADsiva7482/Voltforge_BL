package in.voltforge.api.component.service.impl;

import in.voltforge.api.common.exception.ResourceNotFoundException;
import in.voltforge.api.component.dto.ComponentResponse;
import in.voltforge.api.component.entity.ElectronicComponent;
import in.voltforge.api.component.mapper.ComponentMapper;
import in.voltforge.api.component.repository.ComponentRepository;
import in.voltforge.api.component.service.ComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
}
