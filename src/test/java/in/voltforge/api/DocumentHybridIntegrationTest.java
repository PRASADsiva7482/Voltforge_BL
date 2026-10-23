package in.voltforge.api;

import in.voltforge.api.ai.document.*;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import in.voltforge.api.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.profiles.active=test","app.ai.documents.hybrid-enabled=true",
    "spring.datasource.url=jdbc:h2:mem:hybrid;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","logging.level.root=WARN","logging.level.org.springframework=WARN"})
class DocumentHybridIntegrationTest {
    @Autowired DocumentService documents;
    @Autowired DocumentExtractor extractor;
    @Autowired DocumentEmbeddingRepository embeddings;
    @Autowired UserRepository users;
    @Autowired in.voltforge.api.project.repository.ProjectRepository projects;
    @MockitoBean DocumentEmbeddingClient client;
    @MockitoBean JwtDecoder jwt;
    String owner,other;
    @BeforeEach void setup() {
        owner="hybrid-"+UUID.randomUUID(); other="other-"+UUID.randomUUID();
        for(String subject:List.of(owner,other)) users.saveAndFlush(User.builder().keycloakId(subject).username(subject).email(subject+"@example.invalid").build());
    }
    DocumentService.Summary upload(String body) { return documents.save(owner,null,null,"Reference",extractor.extract(body.getBytes(StandardCharsets.UTF_8),"r.txt")); }
    List<Double> vector(int index) { var v=new ArrayList<>(Collections.nCopies(384,0.0)); v.set(index,1.0); return v; }
    void model(String id) {
        var m=new DocumentEmbeddingClient.Model(id,384,true); when(client.model()).thenReturn(m);
        when(client.encode(eq(m),anyList())).thenAnswer(i->{List<String> texts=i.getArgument(1); return texts.stream().map(t->vector(0)).toList();});
    }
    @Test void semanticNoKeywordMatchCachesRebuildsAndDeletesDerivedData() {
        model("a".repeat(64)); var doc=upload("A resistor limits electrical current through a light emitting diode.");
        var source=documents.retrieve(owner,null,List.of(doc.id()),"prevent lamp burnout").getFirst();
        String chunk=(String)source.get("sourceId");
        assertThat(embeddings.findByChunkIdInAndModelId(List.of(chunk),"a".repeat(64))).hasSize(1);
        clearInvocations(client);
        documents.retrieve(owner,null,List.of(doc.id()),"prevent lamp burnout");
        verify(client,times(1)).encode(any(),anyList()); // Query only; stored source reused.
        model("b".repeat(64)); documents.retrieve(owner,null,List.of(doc.id()),"prevent lamp burnout");
        assertThat(embeddings.findByChunkIdInAndModelId(List.of(chunk),"b".repeat(64))).hasSize(1);
        var updated=documents.save(owner,null,doc.id(),"New version",extractor.extract("Changed authoritative text".getBytes(StandardCharsets.UTF_8),"r.txt"));
        var next=documents.retrieve(owner,null,List.of(updated.id()),"prevent lamp burnout").getFirst();
        assertThat(next.get("versionId")).isNotEqualTo(source.get("versionId"));
        assertThat(documents.readChunk(owner,doc.id(),(String)source.get("versionId"),chunk).get("content")).asString().contains("resistor");
        documents.delete(owner,doc.id());
        assertThat(embeddings.findByChunkIdInAndModelId(List.of(chunk),"a".repeat(64))).isEmpty();
        assertThat(embeddings.findByChunkIdInAndModelId(List.of(chunk),"b".repeat(64))).isEmpty();
    }
    @Test void unauthorizedOwnerNeverReachesEncoderAndFailureDoesNotDestroySource() {
        var doc=upload("Private reference");
        assertThatThrownBy(()->documents.retrieve(other,null,List.of(doc.id()),"meaning")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(client);
        when(client.model()).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(()->documents.retrieve(owner,null,List.of(doc.id()),"meaning")).isInstanceOf(IllegalStateException.class);
        assertThat(documents.list(owner,null,"",0).getContent()).extracting(DocumentService.Summary::id).contains(doc.id());
    }
    @Test void projectConsentIsCheckedBeforePrivateEncoding() {
        var project=new in.voltforge.api.project.entity.Project(); project.setOwner(users.findByKeycloakId(owner).orElseThrow());
        project.setName("Private scope"); project.setBoardType(in.voltforge.api.common.enums.BoardType.ARDUINO_UNO); projects.saveAndFlush(project);
        var doc=documents.save(owner,project.getId(),null,"Scoped reference",extractor.extract("A resistor limits current".getBytes(StandardCharsets.UTF_8),"r.txt"));
        assertThatThrownBy(()->documents.retrieve(owner,null,List.of(doc.id()),"current")).isInstanceOf(in.voltforge.api.common.exception.ForbiddenException.class);
        verifyNoInteractions(client);
        model("a".repeat(64));
        assertThat(documents.retrieve(owner,project.getId(),List.of(doc.id()),"current")).hasSize(1);
    }
    @Test void actualEmbeddingFixtureMatchesProductionFusionQuality() throws Exception {
        var fixture=JsonMapper.builder().build().readTree(getClass().getResourceAsStream("/embedding-eval.json"));
        var rows=new ArrayList<DocumentChunk>(); Map<String,List<Double>> vectors=new HashMap<>();
        for(int i=0;i<fixture.get("documents").size();i++) {
            var c=new DocumentChunk(); c.setId(Integer.toString(i)); c.setContent(fixture.get("documents").get(i).asText()); rows.add(c);
            var v=new ArrayList<Double>(); fixture.get("vectors").get(i).forEach(n->v.add(n.asDouble())); vectors.put(c.getId(),v);
        }
        int exact=0,paraphrase=0,recall=0;
        for(var query:fixture.get("queries")) {
            var v=new ArrayList<Double>(); query.get("vector").forEach(n->v.add(n.asDouble()));
            var terms=Arrays.stream(query.get("text").asText().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_.-]+"))
                .filter(t->t.length()>2&&!Set.of("the","this","that","with","from","what","does","explain","please","about","document","and","for","are","how").contains(t)).toList();
            var ranked=DocumentHybridSearch.fuse(rows,vectors,v,terms); String expected=query.get("expected").asText();
            if(ranked.getFirst().getId().equals(expected)) { if(query.get("kind").asText().equals("exact")) exact++; else paraphrase++; }
            if(ranked.stream().limit(3).anyMatch(r->r.getId().equals(expected))) recall++;
        }
        assertThat(exact).isEqualTo(5); assertThat(paraphrase).isGreaterThanOrEqualTo(11); assertThat(recall).isEqualTo(17);
        for(var query:fixture.get("negativeQueries")) {
            var v=new ArrayList<Double>(); query.get("vector").forEach(n->v.add(n.asDouble()));
            var terms=Arrays.stream(query.get("text").asText().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_.-]+"))
                .filter(t->t.length()>2).toList();
            assertThat(DocumentHybridSearch.fuse(rows,vectors,v,terms)).as(query.get("text").asText()).isEmpty();
        }
    }
}
