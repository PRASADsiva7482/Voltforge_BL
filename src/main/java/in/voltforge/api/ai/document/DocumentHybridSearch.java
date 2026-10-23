package in.voltforge.api.ai.document;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
public class DocumentHybridSearch {
    private final DocumentEmbeddingClient client;
    private final DocumentEmbeddingRepository embeddings;
    private final DocumentEmbeddingStore store;
    @Value("${app.ai.documents.hybrid-enabled:false}") private boolean enabled;
    public boolean enabled() { return enabled; }

    public List<DocumentChunk> rank(String subject,List<DocumentChunk> authorized,String query,List<String> terms) {
        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(20).toNanos();
        var model=client.model();
        var ids=authorized.stream().map(DocumentChunk::getId).toList();
        Map<String,List<Double>> vectors=new HashMap<>();
        embeddings.findByChunkIdInAndModelId(ids,model.modelId()).forEach(e->{validateVector(e.getEmbedding()); vectors.put(e.getChunkId(),e.getEmbedding());});
        var missing=authorized.stream().filter(c->!vectors.containsKey(c.getId())).toList();
        for(int offset=0;offset<missing.size();offset+=16) {
            if(System.nanoTime()>deadline) throw warming();
            var batch=missing.subList(offset,Math.min(offset+16,missing.size()));
            var encoded=client.encode(model,batch.stream().map(DocumentChunk::getContent).toList());
            store.save(subject,model.modelId(),batch,encoded);
            for(int i=0;i<batch.size();i++) vectors.put(batch.get(i).getId(),encoded.get(i));
        }
        if(System.nanoTime()>deadline) throw warming();
        var question=client.encode(model,List.of(query.substring(0,Math.min(2000,query.length())))).getFirst();
        return fuse(authorized,vectors,question,terms);
    }
    public static void validateVector(List<Double> vector) {
        if(vector==null||vector.size()!=384||vector.stream().anyMatch(v->v==null||!Double.isFinite(v))) throw new IllegalArgumentException("Invalid embedding vector");
        double norm=vector.stream().mapToDouble(v->v*v).sum();
        if(norm<0.98||norm>1.02) throw new IllegalArgumentException("Embedding is not normalized");
    }
    private static in.voltforge.api.ai.gateway.AiGatewayException warming() {
        return new in.voltforge.api.ai.gateway.AiGatewayException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "AI_EMBEDDING_WARMING","Some references are still being indexed. Retry to continue; completed indexing is preserved.",true);
    }
    public static List<DocumentChunk> fuse(List<DocumentChunk> rows,Map<String,List<Double>> vectors,List<Double> question,List<String> terms) {
        var identifiers=terms.stream().filter(t->t.matches("[a-z][a-z0-9_.-]*")&&t.matches(".*[0-9].*")).map(t->Pattern.compile("(?<![\\p{L}\\p{N}_.-])"+Pattern.quote(t)+"(?![\\p{L}\\p{N}_.-])",Pattern.CASE_INSENSITIVE)).toList();
        Map<String,Double> semantic=new HashMap<>(); Map<String,Integer> lexical=new HashMap<>(), exact=new HashMap<>();
        for(var row:rows) {
            var v=vectors.get(row.getId()); double dot=0; for(int i=0;i<384;i++) dot+=question.get(i)*v.get(i);
            String text=row.getContent().toLowerCase(Locale.ROOT);
            semantic.put(row.getId(),dot);
            lexical.put(row.getId(),terms.stream().filter(text::contains).mapToInt(t->t.matches(".*\\d.*")?4:1).sum());
            exact.put(row.getId(),(int)identifiers.stream().filter(p->p.matcher(text).find()).count());
        }
        int max=Math.max(1,lexical.values().stream().mapToInt(Integer::intValue).max().orElse(1));
        // Identifiers are a hard relevance signal; semantic similarity leads otherwise.
        return rows.stream().filter(r->identifiers.isEmpty()?semantic.get(r.getId())>=0.2:exact.get(r.getId())>0)
            .sorted(Comparator.<DocumentChunk>comparingInt(r->exact.get(r.getId())).reversed()
                .thenComparing(Comparator.<DocumentChunk>comparingDouble(r->semantic.get(r.getId())+0.02*lexical.get(r.getId())/max).reversed())
                .thenComparing(DocumentChunk::getId)).limit(5).toList();
    }
}
