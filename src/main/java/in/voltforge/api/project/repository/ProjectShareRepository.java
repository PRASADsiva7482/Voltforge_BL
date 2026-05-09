package in.voltforge.api.project.repository;

import in.voltforge.api.project.entity.ProjectShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectShareRepository extends JpaRepository<ProjectShare, String> {

    List<ProjectShare> findByProjectId(String projectId);

    List<ProjectShare> findBySharedWithUserId(String userId);

    Optional<ProjectShare> findByProjectIdAndSharedWithUserId(String projectId, String userId);

    boolean existsByProjectIdAndSharedWithUserId(String projectId, String userId);

    void deleteByProjectIdAndSharedWithUserId(String projectId, String userId);
}
