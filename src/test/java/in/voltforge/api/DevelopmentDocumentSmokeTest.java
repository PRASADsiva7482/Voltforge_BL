package in.voltforge.api;

import in.voltforge.api.ai.document.*;
import in.voltforge.api.ai.conversation.*;
import in.voltforge.api.ai.dto.AiResearchRequest;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.*;
import java.util.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real development DB + real local model. Only synthetic account data removed. */
@EnabledIfEnvironmentVariable(named="VF_DOCUMENT_DEV_SMOKE",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=dev","spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false",
    "app.ai.model.url=${VF_DOCUMENT_AI_URL:http://127.0.0.1:2002/voltForge-ai}",
    "server.address=127.0.0.1","logging.level.root=WARN","logging.level.org.springframework=WARN",
    "logging.level.in.voltforge.api=WARN","logging.level.org.hibernate.SQL=WARN"
})
class DevelopmentDocumentSmokeTest {
    @Autowired Environment environment;
    @Autowired UserRepository users;
    @Autowired DocumentService documents;
    @Autowired DocumentExtractor extractor;
    @Autowired ConversationStore conversations;
    @Autowired ConversationStream stream;
    @Autowired DocumentEmbeddingRepository embeddingRows;
    @Autowired DocumentEmbeddingClient encoder;
    @MockitoBean JwtDecoder jwtDecoder;
    @Test void actualDatasheetRetrievalNativeStreamPersistenceAndReopen() throws Exception {
        assertThat(environment.getProperty("app.ai.environment")).isEqualTo("development");
        byte[] pdf=Files.readAllBytes(Path.of("target/A000066-verification.pdf"));
        String subject="document-smoke-"+UUID.randomUUID();
        var user=users.saveAndFlush(User.builder().keycloakId(subject).username(subject).email(subject+"@example.invalid").build());
        String document=null,conversation=null;
        try {
            var extracted=extractor.extract(pdf,"A000066.pdf");
            var doc=documents.save(subject,null,null,"Arduino UNO R3 datasheet",extracted); document=doc.id();
            var c=conversations.create(subject,null); conversation=c.id();
            var request=new ConversationRequests.Send(); request.setClientRequestId(UUID.randomUUID().toString());
            request.setMessage("Which processor is the ATmega328P? Answer in one sentence using the selected Arduino reference and cite its source ID.");
            request.setResearch(new AiResearchRequest(null,null,false,List.of(doc.id())));
            var turn=conversations.begin(subject,c.id(),request);
            assertThat(turn.request().getAuthorizedDocumentEvidence()).isNotEmpty();
            var sourceIds=turn.request().getAuthorizedDocumentEvidence().stream().map(s->(String)s.get("sourceId")).toList();
            var model=encoder.model();
            assertThat(embeddingRows.findByChunkIdInAndModelId(sourceIds,model.modelId())).hasSize(sourceIds.size());
            var reopenedSources=documents.retrieve(subject,null,List.of(doc.id()),request.getMessage());
            assertThat(reopenedSources.stream().map(s->s.get("sourceId")).toList()).containsExactlyElementsOf(sourceIds);
            var times=new ArrayList<Long>(); long start=System.nanoTime();
            stream.stream(turn).doOnNext(e->{if("token".equals(e.event())) times.add(System.nanoTime()-start);}).blockLast(Duration.ofMinutes(3));
            var restored=conversations.history(subject,c.id(),0,50).getContent();
            assertThat(restored).hasSize(2); assertThat(restored.getFirst().status()).as("error code: %s",restored.getFirst().metadata().get("errorCode")).isEqualTo("completed");
            assertThat(restored.getFirst().content()).isNotBlank(); assertThat(times.size()).isGreaterThan(1);
            assertThat(restored.getFirst().content()).containsIgnoringCase("ATmega328P").containsPattern("\\[S[1-5]\\]");
            assertThat(restored.getFirst().metadata()).containsKey("citations");
            var citations=(List<?>)restored.getFirst().metadata().get("citations"); assertThat(citations).isNotEmpty();
            var citation=(Map<?,?>)citations.getFirst();
            assertThat(citation.get("documentId")).isEqualTo(doc.id());
            assertThat(citation.get("locator")).isEqualTo("document_chunk");
            var excerpt=documents.readChunk(subject,doc.id(),(String)citation.get("versionId"),(String)citation.get("sourceId"));
            assertThat(excerpt.get("page")).isEqualTo(((Number)citation.get("page")).intValue());
            assertThat(conversations.begin(subject,c.id(),request).replay()).isTrue();
            System.out.printf("DOCUMENT_DEV_VERIFIED pages=%d chunks=%d nativeTokens=%d sources=%d persistedMessages=%d sourcePage=%s firstTokenMs=%d%n",extracted.pages(),extracted.chunks().size(),times.size(),citations.size(),restored.size(),citation.get("page"),times.getFirst()/1_000_000);
        } finally {
            if(conversation!=null) { conversations.stop(subject,conversation); conversations.delete(subject,conversation); }
            if(document!=null) documents.delete(subject,document);
            users.deleteById(user.getId());
        }
    }
}
