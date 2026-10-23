package in.voltforge.api.ai.document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk,String> {
    void deleteByVersionIdIn(List<String> versions);
}
