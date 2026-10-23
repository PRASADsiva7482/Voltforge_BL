package in.voltforge.api.ai.document;

import in.voltforge.api.common.exception.*;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Authoritative document scope, immutable versions and bounded lexical retrieval. */
@Service @RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentChunkRepository chunks;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final EntityManager em;
    private final DocumentHybridSearch hybrid;
    public record Summary(String id,String title,String projectId,int currentVersion) {
        static Summary of(ReferenceDocument d) { return new Summary(d.getId(),d.getTitle(),d.getProjectId(),d.getCurrentVersion()); }
    }
    private String owner(String subject) { return users.findByKeycloakId(subject).orElseThrow(()->new ForbiddenException("Registered account required")).getId(); }
    private void project(String owner,String id) {
        if(id!=null && !projects.findById(id).map(p->p.getOwner().getId().equals(owner)).orElse(false)) throw new ForbiddenException("Document project is not owned by this account");
    }
    private ReferenceDocument owned(String subject,String id) {
        String owner=owner(subject);
        var doc=documents.findByIdAndOwnerId(id,owner).orElseThrow(()->new ResourceNotFoundException("Document","id",id));
        project(owner,doc.getProjectId()); return doc;
    }
    @Transactional(readOnly=true) public void checkUpload(String subject,String projectId,String documentId) {
        if(documentId!=null) owned(subject,documentId); else project(owner(subject),projectId);
    }
    @Transactional(readOnly=true) public Page<Summary> list(String subject,String projectId,String q,int page) {
        String owner=owner(subject); project(owner,projectId);
        if(page<0||q.length()>120) throw new BadRequestException("Invalid document search/page");
        String query=q.toLowerCase(Locale.ROOT).strip().replace("!","!!").replace("%","!%").replace("_","!_");
        return documents.summaries(owner,projectId,"%"+query+"%",PageRequest.of(page,20)).map(Summary::of);
    }
    @Transactional public Summary save(String subject,String projectId,String documentId,String title,DocumentExtractor.Extracted extracted) {
        String owner=users.lockMemoryOwner(subject).orElseThrow(()->new ForbiddenException("Registered account required")).getId();
        project(owner,projectId);
        if(title==null||title.isBlank()||title.strip().length()>200) throw new BadRequestException("Document title must contain 1–200 characters");
        ReferenceDocument doc;
        if(documentId==null) {
            // Owner lock serializes deduplication/quota checks, including concurrent uploads.
            var duplicate=em.createQuery("SELECT d FROM ReferenceDocument d, DocumentVersion v WHERE d.id=v.documentId AND d.ownerId=:owner AND (d.projectId=:project OR (d.projectId IS NULL AND :project IS NULL)) AND v.contentHash=:hash",ReferenceDocument.class)
                .setParameter("owner",owner).setParameter("project",projectId).setParameter("hash",extracted.hash()).setMaxResults(1).getResultList();
            if(!duplicate.isEmpty()) return Summary.of(duplicate.getFirst());
            if(documents.countByOwnerId(owner)>=50) throw new BadRequestException("Document limit reached (50 per account)");
            doc=new ReferenceDocument(); doc.setOwnerId(owner); doc.setProjectId(projectId); doc.setTitle(title.strip());
        } else {
            doc=owned(subject,documentId);
            if(versions.findByDocumentIdAndContentHash(doc.getId(),extracted.hash()).isPresent()) return Summary.of(doc);
            if(doc.getCurrentVersion()>=10) throw new BadRequestException("Version limit reached (10 per document)");
            doc.setTitle(title.strip());
        }
        doc.setCurrentVersion(doc.getCurrentVersion()+1); documents.saveAndFlush(doc);
        var version=new DocumentVersion(); version.setDocumentId(doc.getId()); version.setVersionNumber(doc.getCurrentVersion());
        version.setContentHash(extracted.hash()); version.setMediaType(extracted.mediaType()); version.setPageCount(extracted.pages());
        version.setChunkCount(extracted.chunks().size()); version.setStatus("ready"); versions.saveAndFlush(version);
        int ordinal=0;
        for(var part:extracted.chunks()) {
            var chunk=new DocumentChunk(); chunk.setVersionId(version.getId()); chunk.setOrdinal(++ordinal); chunk.setPageNumber(part.page()); chunk.setContent(part.text()); chunks.save(chunk);
        }
        chunks.flush(); return Summary.of(doc);
    }
    @Transactional public void delete(String subject,String id) {
        users.lockMemoryOwner(subject).orElseThrow(()->new ForbiddenException("Registered account required"));
        var doc=owned(subject,id);
        var ids=versions.findByDocumentId(id).stream().map(DocumentVersion::getId).toList();
        if(!ids.isEmpty()) chunks.deleteByVersionIdIn(ids);
        versions.deleteByDocumentId(id); documents.delete(doc);
    }
    @Transactional(readOnly=true) public Map<String,Object> readChunk(String subject,String documentId,String versionId,String chunkId) {
        var doc=owned(subject,documentId);
        var version=versions.findById(versionId).filter(v->v.getDocumentId().equals(documentId)).orElseThrow(()->new ResourceNotFoundException("Document version","id",versionId));
        var chunk=chunks.findById(chunkId).filter(c->c.getVersionId().equals(versionId)).orElseThrow(()->new ResourceNotFoundException("Document chunk","id",chunkId));
        return Map.of("title",doc.getTitle(),"version",version.getVersionNumber(),"page",chunk.getPageNumber(),"content",chunk.getContent());
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> retrieve(String subject,String projectId,List<String> selected,String query) {
        if(selected==null||selected.isEmpty()) return List.of();
        if(selected.size()>10||new HashSet<>(selected).size()!=selected.size()) throw new BadRequestException("Choose up to 10 distinct documents");
        Map<String,DocumentVersion> byVersion=new LinkedHashMap<>();
        Map<String,ReferenceDocument> byDocument=new HashMap<>();
        for(String id:selected) {
            var doc=owned(subject,id);
            if(doc.getProjectId()!=null&&!Objects.equals(projectId,doc.getProjectId())) throw new ForbiddenException("Enable context sharing in the document's project conversation before using it");
            var version=versions.findByDocumentIdAndVersionNumber(id,doc.getCurrentVersion()).orElseThrow(()->new BadRequestException("Document is not indexed; upload it again"));
            byVersion.put(version.getId(),version); byDocument.put(id,doc);
        }
        var terms=Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_.-]+"))
            .filter(t->t.length()>2&&t.length()<=80&&!Set.of("the","this","that","with","from","what","does","explain","please","about","document","and","for","are","how").contains(t)).distinct().limit(16).toList();
        if(terms.isEmpty()) throw new BadRequestException("Ask about a part number or specific topic in the selected documents");
        var cb=em.getCriteriaBuilder(); var criteria=cb.createQuery(DocumentChunk.class); var row=criteria.from(DocumentChunk.class);
        Expression<Integer> score=cb.literal(0);
        for(String term:terms) {
            var match=cb.like(cb.lower(row.get("content")),"%"+term.replace("_","!_")+"%",'!');
            score=cb.sum(score,cb.<Integer>selectCase().when(match,term.matches(".*\\d.*")?4:1).otherwise(0));
        }
        criteria.select(row).where(cb.and(row.get("versionId").in(byVersion.keySet()),cb.gt(score,0)))
            .orderBy(cb.desc(score),cb.asc(row.get("versionId")),cb.asc(row.get("ordinal")));
        List<DocumentChunk> matches;
        if(hybrid.enabled()) {
            // Scope in SQL before loading text or asking the private encoder.
            var authorized=em.createQuery("SELECT c FROM DocumentChunk c WHERE c.versionId IN :versions ORDER BY c.versionId,c.ordinal",DocumentChunk.class)
                .setParameter("versions",byVersion.keySet()).setMaxResults(2561).getResultList();
            if(authorized.size()>2560) throw new BadRequestException("Select fewer documents for retrieval");
            matches=hybrid.rank(subject,authorized,query,terms);
        } else matches=em.createQuery(criteria).setMaxResults(5).getResultList();
        if(matches.isEmpty()) throw new BadRequestException("No matching text in the selected documents. Try an exact part number or another term");
        return matches.stream().map(chunk->{
            var v=byVersion.get(chunk.getVersionId()); var d=byDocument.get(v.getDocumentId());
            Map<String,Object> source=new LinkedHashMap<>();
            source.put("sourceId",chunk.getId()); source.put("kind","document_chunk"); source.put("documentId",d.getId()); source.put("versionId",v.getId()); source.put("page",chunk.getPageNumber());
            source.put("title",d.getTitle()+" · v"+v.getVersionNumber()); source.put("retrievedAt",java.time.Instant.now().toString());
            // A compact window around the strongest query term avoids spending
            // the small local model's prefill budget on unrelated page headers.
            String text=chunk.getContent(), lower=text.toLowerCase(Locale.ROOT);
            var strongest=terms.stream().filter(lower::contains).sorted(Comparator.<String>comparingInt(t->t.matches(".*\\d.*")?4:1).reversed().thenComparing(Comparator.comparingInt(String::length).reversed())).findFirst().orElse("");
            int start=Math.max(0,lower.indexOf(strongest)-40), end=Math.min(text.length(),start+800);
            if(start>0&&Character.isLowSurrogate(text.charAt(start))) start--;
            if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1))) end--;
            source.put("excerpt",text.substring(start,end)); source.put("contentHash",v.getContentHash());
            return source;
        }).toList();
    }
}
