package in.voltforge.api.ai.document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion,String> {
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(String document,int version);
    Optional<DocumentVersion> findByDocumentIdAndContentHash(String document,String hash);
    List<DocumentVersion> findByDocumentId(String document);
    void deleteByDocumentId(String document);
}
