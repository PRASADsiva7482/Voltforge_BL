package in.voltforge.api.component.service;

import in.voltforge.api.component.dto.ComponentResponse;
import in.voltforge.api.component.dto.CustomComponentRequest;

import java.util.List;

public interface ComponentService {

    List<ComponentResponse> getAllComponents();

    List<ComponentResponse> getComponentsByCategory(String category);

    List<ComponentResponse> getFreeComponents();

    List<ComponentResponse> searchComponents(String query);

    ComponentResponse getComponentById(String id);

    List<String> getAllCategories();

    ComponentResponse createCustomComponent(CustomComponentRequest request);

    List<ComponentResponse> getCommunityComponents();
}
