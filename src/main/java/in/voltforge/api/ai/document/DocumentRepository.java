package in.voltforge.api.ai.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<ReferenceDocument,String> {
    Optional<ReferenceDocument> findByIdAndOwnerId(String id,String owner);
    long countByOwnerId(String owner);
    @Query("SELECT d FROM ReferenceDocument d WHERE d.ownerId=:owner AND (d.projectId IS NULL OR d.projectId=:project) AND LOWER(d.title) LIKE :query ESCAPE '!' ORDER BY d.updatedAt DESC, d.id")
    Page<ReferenceDocument> summaries(String owner,String project,String query,Pageable pageable);
}
