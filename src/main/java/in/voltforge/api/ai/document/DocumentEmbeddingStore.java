package in.voltforge.api.ai.document;

import in.voltforge.api.user.repository.UserRepository;
import in.voltforge.api.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DocumentEmbeddingStore {
    private final DocumentEmbeddingRepository repository;
    private final UserRepository users;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void save(String subject,String model,List<DocumentChunk> chunks,List<List<Double>> vectors) {
        // Serialize this owner's cache writes; duplicate sends cannot race inserts.
        users.lockMemoryOwner(subject).orElseThrow(()->new ForbiddenException("Registered account required"));
        var existing=new HashSet<>(repository.findByChunkIdInAndModelId(chunks.stream().map(DocumentChunk::getId).toList(),model).stream().map(DocumentEmbedding::getChunkId).toList());
        for(int i=0;i<chunks.size();i++) if(!existing.contains(chunks.get(i).getId())) {
            var row=new DocumentEmbedding(); row.setChunkId(chunks.get(i).getId()); row.setModelId(model); row.setEmbedding(vectors.get(i)); repository.save(row);
        }
        repository.flush();
    }
}
