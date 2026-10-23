package in.voltforge.api.ai.document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding,String> {
    List<DocumentEmbedding> findByChunkIdInAndModelId(List<String> ids,String modelId);
}
