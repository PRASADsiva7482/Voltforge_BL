package in.voltforge.api;

import in.voltforge.api.ai.document.*;
import in.voltforge.api.ai.conversation.*;
import in.voltforge.api.ai.dto.AiResearchRequest;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.common.exception.ForbiddenException;
import in.voltforge.api.common.exception.ResourceNotFoundException;
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
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

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
    @Autowired ProjectRepository projects;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @MockitoBean JwtDecoder jwtDecoder;
    @Test void liveProjectIsolationConsentCancellationRetryAndSourceCleanup() throws Exception {
        assertThat(environment.getProperty("app.ai.environment")).isEqualTo("development");
        String subject="rag-owner-"+UUID.randomUUID(), stranger="rag-other-"+UUID.randomUUID();
        String ownerToken=UUID.randomUUID().toString(), otherToken=UUID.randomUUID().toString();
        var createdUsers=new ArrayList<User>();
        String projectId=null, documentId=null, conversationId=null;
        var mapper=new tools.jackson.databind.json.JsonMapper();
        try (var http=HttpClient.newHttpClient()) {
            for(String name:List.of(subject,stranger)) createdUsers.add(users.saveAndFlush(User.builder()
                .keycloakId(name).username(name).email(name+"@example.invalid").build()));
            when(jwtDecoder.decode(ownerToken)).thenReturn(org.springframework.security.oauth2.jwt.Jwt.withTokenValue(ownerToken)
                .header("alg","RS256").subject(subject).build());
            when(jwtDecoder.decode(otherToken)).thenReturn(org.springframework.security.oauth2.jwt.Jwt.withTokenValue(otherToken)
                .header("alg","RS256").subject(stranger).build());
            var project=Project.builder().owner(createdUsers.getFirst()).name("Synthetic RAG acceptance")
                .description("Synthetic project data; never belongs to a real customer").build();
            projects.saveAndFlush(project); projectId=project.getId();
            var extracted=extractor.extract("ZX314 is a synthetic test component. Its supply voltage is 3.3 V. This reference is test data, not a real component specification."
                .getBytes(StandardCharsets.UTF_8),"synthetic.txt");
            var doc=documents.save(subject,projectId,null,"Synthetic project reference",extracted); documentId=doc.id();
            var conversation=conversations.create(subject,projectId); conversationId=conversation.id();
            assertThat(documents.list(stranger,null,"",0)).isEmpty();
            assertThat(conversations.list(stranger,null,"",0,20)).isEmpty();
            assertThatThrownBy(()->conversations.create(stranger,project.getId())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(()->documents.retrieve(stranger,project.getId(),List.of(doc.id()),"ZX314"))
                .isInstanceOf(ResourceNotFoundException.class);

            String base="http://127.0.0.1:"+port+"/voltForge-app/api/v1/ai";
            String chat=base+"/conversations/"+conversation.id();
            assertThat(httpStatus(http,chat+"/messages",null,"GET",null)).isEqualTo(401);
            for(String suffix:List.of("","/messages"))
                assertThat(httpStatus(http,chat+suffix,otherToken,"GET",null)).isEqualTo(404);
            assertThat(httpStatus(http,chat,otherToken,"PATCH",mapper.writeValueAsString(Map.of("title","Denied")))).isEqualTo(404);
            assertThat(httpStatus(http,chat,otherToken,"DELETE",null)).isEqualTo(404);
            assertThat(httpStatus(http,chat+"/stop",otherToken,"POST","{}")).isEqualTo(404);
            var sendBody=mapper.writeValueAsString(Map.of("message","ZX314 supply voltage", "clientRequestId",UUID.randomUUID().toString()));
            assertThat(httpStatus(http,chat+"/messages/stream",otherToken,"POST",sendBody)).isEqualTo(404);

            // Sharing off must strip every supplied private context category at the BL boundary.
            var off=new ConversationRequests.Send(); off.setClientRequestId(UUID.randomUUID().toString());
            off.setMessage("What is a resistor?"); off.setProjectId(project.getId());
            off.setCode("PRIVATE_FIRMWARE"); off.setContext("PRIVATE_CONTEXT"); off.setCanvasContext("PRIVATE_CANVAS");
            off.setBoardType("arduino-uno"); off.setComponents(List.of(Map.of("id","private-component")));
            off.setWires(List.of(Map.of("id","private-wire"))); off.setNetlist(Map.of("private",true));
            off.setCanvasData(Map.of("private",true)); off.setSimulationState(Map.of("serialOutput","PRIVATE_SERIAL"));
            off.setFiles(List.of(Map.of("content","PRIVATE_FILE"))); off.setDiagnostics(List.of(Map.of("message","PRIVATE_DIAGNOSTIC")));
            off.setMemory(List.of(Map.of("content","PRIVATE_MEMORY")));
            off.setAuthorizedDocumentEvidence(List.of(Map.of("excerpt","FORGED_EVIDENCE")));
            var offTurn=conversations.begin(subject,conversation.id(),off);
            var safe=offTurn.request();
            assertThat(Arrays.asList(safe.getProjectId(),safe.getCode(),safe.getContext(),safe.getCanvasContext(),safe.getBoardType(),
                safe.getComponents(),safe.getWires(),safe.getNetlist(),safe.getCanvasData(),safe.getSimulationState(),
                safe.getFiles(),safe.getDiagnostics(),safe.getMemory())).containsOnlyNulls();
            assertThat(safe.getAuthorizedDocumentEvidence()).isEmpty();
            conversations.stop(subject,conversation.id());

            var send=new ConversationRequests.Send(); send.setClientRequestId(UUID.randomUUID().toString());
            send.setMessage("What is the ZX314 supply voltage in this synthetic reference? Answer in one short sentence and cite the source ID.");
            send.setResearch(new AiResearchRequest(null,null,false,List.of(doc.id())));
            assertThatThrownBy(()->conversations.begin(subject,conversation.id(),send)).isInstanceOf(ForbiddenException.class);
            assertThat(conversations.history(subject,conversation.id(),0,50).getTotalElements()).isEqualTo(2);
            send.setIncludeProjectContext(true); send.setProjectId(project.getId());
            var first=conversations.begin(subject,conversation.id(),send);
            assertThat(first.request().getProjectId()).isEqualTo(project.getId());
            var source=first.request().getAuthorizedDocumentEvidence().getFirst();
            var chunkIds=first.request().getAuthorizedDocumentEvidence().stream().map(s->(String)s.get("sourceId")).toList();
            var model=encoder.model();
            assertThat(embeddingRows.findByChunkIdInAndModelId(chunkIds,model.modelId())).hasSize(chunkIds.size());
            String sourceUrl=base+"/documents/"+doc.id()+"/versions/"+source.get("versionId")+"/chunks/"+source.get("sourceId");
            assertThat(httpStatus(http,sourceUrl,ownerToken,"GET",null)).isEqualTo(200);
            assertThat(httpStatus(http,sourceUrl,otherToken,"GET",null)).isEqualTo(404);
            assertThat(httpStatus(http,base+"/documents/"+doc.id(),otherToken,"DELETE",null)).isEqualTo(404);

            // Cancel actual upstream generation after a real token, then await asynchronous DB cleanup.
            var received=stream.stream(first).takeUntil(e->"token".equals(e.event())).collectList().block(Duration.ofMinutes(3));
            assertThat(received).anyMatch(e->"token".equals(e.event()));
            long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(conversations.history(subject,conversation.id(),0,50).getContent().getFirst().status().equals("pending")
                    && System.nanoTime()<deadline) Thread.sleep(50);
            var interrupted=conversations.history(subject,conversation.id(),0,50).getContent().getFirst();
            assertThat(interrupted.status()).isEqualTo("interrupted");
            assertThat(interrupted.content()).isNotBlank();

            send.setRetry(true);
            var retry=conversations.begin(subject,conversation.id(),send);
            assertThat(retry.assistant().id()).isEqualTo(first.assistant().id());
            assertThat(retry.attemptId()).isNotEqualTo(first.attemptId());
            // A late checkpoint from the cancelled attempt cannot overwrite the new attempt.
            assertThat(conversations.checkpoint(first,"STALE_RESPONSE","completed",Map.of()).status()).isEqualTo("pending");
            var events=stream.stream(retry).collectList().block(Duration.ofMinutes(3));
            assertThat(events).anyMatch(e->"saved".equals(e.event()));
            var restored=conversations.history(subject,conversation.id(),0,50).getContent();
            assertThat(restored).hasSize(4);
            assertThat(restored.getFirst().status()).as("error code: %s",restored.getFirst().metadata().get("errorCode")).isEqualTo("completed");
            assertThat(restored.getFirst().content()).isNotBlank().doesNotContain("STALE_RESPONSE");
            assertThat(restored.getFirst().metadata()).containsKey("citations");
            var citations=(List<?>)restored.getFirst().metadata().get("citations");
            assertThat(citations).isNotEmpty();
            assertThat(((Map<?,?>)citations.getFirst()).get("documentId")).isEqualTo(doc.id());
            assertThat(conversations.begin(subject,conversation.id(),send).replay()).isTrue();
            var reopen=http.send(HttpRequest.newBuilder(URI.create(chat+"/messages")).header("Authorization","Bearer "+ownerToken)
                .GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(reopen.statusCode()).isEqualTo(200);
            assertThat(reopen.body()).contains(restored.getFirst().id());
            assertThat(conversations.history(subject,conversation.id(),0,50).getTotalElements()).isEqualTo(4);

            documents.delete(subject,doc.id()); documentId=null;
            assertThat(httpStatus(http,sourceUrl,ownerToken,"GET",null)).isEqualTo(404);
            assertThat(embeddingRows.findByChunkIdInAndModelId(chunkIds,model.modelId())).isEmpty();
            assertThat(conversations.history(subject,conversation.id(),0,50).getTotalElements()).isEqualTo(4);
            System.out.println("AIR07_DEV_VERIFIED ownership=true sharingOff=true nativeCancelRetry=true staleAttemptRejected=true sourceCleanup=true persistedMessages=4; JWT decoder is a test double, not Keycloak login");
        } finally {
            if(conversationId!=null) { conversations.stop(subject,conversationId); conversations.delete(subject,conversationId); }
            if(documentId!=null) documents.delete(subject,documentId);
            if(projectId!=null) projects.deleteById(projectId);
            for(var user:createdUsers) users.deleteById(user.getId());
        }
    }
    private int httpStatus(HttpClient client,String url,String token,String method,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20));
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.header("Content-Type","application/json");
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.discarding()).statusCode();
    }
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
