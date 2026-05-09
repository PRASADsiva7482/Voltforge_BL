package in.voltforge.api.project.repository;

import in.voltforge.api.project.entity.CodeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeFileRepository extends JpaRepository<CodeFile, String> {

    List<CodeFile> findByProjectIdOrderBySortOrderAsc(String projectId);

    Optional<CodeFile> findByProjectIdAndFilename(String projectId, String filename);

    void deleteByProjectId(String projectId);

    long countByProjectId(String projectId);
}
