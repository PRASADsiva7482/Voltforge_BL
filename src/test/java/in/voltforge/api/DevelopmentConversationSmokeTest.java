package in.voltforge.api;

import in.voltforge.api.ai.conversation.*;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Explicit opt-in: real development MySQL + existing configured AI gateway.
 * Only this test's randomly identified synthetic account/conversation are removed.
 */
@EnabledIfEnvironmentVariable(named="VF_CHAT_DEV_SMOKE",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=dev","spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false",
    "server.address=127.0.0.1","logging.level.root=WARN","logging.level.in.voltforge.api=WARN",
    "logging.level.org.springframework=WARN","logging.level.org.hibernate.SQL=WARN"
})
class DevelopmentConversationSmokeTest {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;
    @Autowired Environment environment;
    @Autowired UserRepository users;
    @Autowired ConversationStore store;
    @Autowired ConversationStream stream;
    @Autowired in.voltforge.api.ai.memory.UserMemoryService memory;
    @Test void realHttpRetryPersistsActualModelResponseAndReopensWithoutDuplicates() throws Exception {
        assertThat(environment.getProperty("app.ai.environment")).isEqualTo("development");
        String subject="chat-http-smoke-"+UUID.randomUUID();
        var user=users.saveAndFlush(User.builder().keycloakId(subject).username(subject).email(subject+"@example.invalid").build());
        String id=null;
        String token="synthetic-http-token";
        when(jwtDecoder.decode(token)).thenReturn(org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token).header("alg","RS256").subject(subject).build());
        try (var client=java.net.http.HttpClient.newHttpClient()) {
            var conversation=store.create(subject,null); id=conversation.id();
            var request=new ConversationRequests.Send(); request.setClientRequestId(UUID.randomUUID().toString());
            request.setMessage("Explain how a resistor limits current. Give a three-paragraph explanation with one numeric example.");
            var failed=store.begin(subject,id,request);
            store.checkpoint(failed,"","failed",java.util.Map.of("errorCode","AI_MODEL_UNAVAILABLE"));
            String base="http://127.0.0.1:"+port+"/voltForge-app/api/v1/ai/conversations/"+id;
            String body="{\"message\":\""+request.getMessage()+"\",\"clientRequestId\":\""+request.getClientRequestId()+"\",\"retry\":true}";
            var httpRequest=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/messages/stream"))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .timeout(Duration.ofMinutes(3)).POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            long began=System.nanoTime();
            var response=client.send(httpRequest,java.net.http.HttpResponse.BodyHandlers.ofLines());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store, no-transform");
            assertThat(response.headers().firstValue("X-Accel-Buffering")).hasValue("no");
            var received=new StringBuilder();
            var tokenTimes=new java.util.ArrayList<Long>();
            try(var lines=response.body()) {
                lines.forEach(line -> {
                    received.append(line).append('\n');
                    if(line.equals("event:token")) tokenTimes.add(System.nanoTime()-began);
                });
            }
            assertThat(received.toString()).contains("event:accepted","event:saved","\"status\":\"completed\"");
            assertThat(tokenTimes).hasSizeGreaterThan(1);
            System.out.printf("Native HTTP streaming: tokens=%d, firstTokenMs=%d, tokenDeliverySpanMs=%d%n",
                tokenTimes.size(),tokenTimes.getFirst()/1_000_000,(tokenTimes.getLast()-tokenTimes.getFirst())/1_000_000);
            var restored=store.history(subject,id,0,50).getContent();
            assertThat(restored).hasSize(2);
            assertThat(restored.getFirst().id()).isEqualTo(failed.assistant().id());
            assertThat(restored.getFirst().content()).isNotBlank();
            assertThat(restored.getFirst().metadata()).doesNotContainKey("errorCode");
            var reopen=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/messages"))
                .header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(reopen.statusCode()).isEqualTo(200);
            assertThat(reopen.body()).contains("\"status\":\"completed\"").doesNotContain("AI_MODEL_UNAVAILABLE");
            var replay=client.send(httpRequest,java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.body()).contains("event:saved");
            assertThat(store.history(subject,id,0,50).getTotalElements()).isEqualTo(2);
            System.out.println("Real HTTP/native-model retry: completed, reopened, idempotent replay, 2 persisted messages");
        } finally {
            if(id!=null) { store.stop(subject,id); store.delete(subject,id); }
            users.deleteById(user.getId());
        }
    }
    @Test void realDatabaseAndProviderRoundTrip() {
        assertThat(environment.getProperty("app.ai.environment")).isEqualTo("development");
        assertThat(org.slf4j.LoggerFactory.getLogger("org.springframework.core.codec.StringDecoder").isDebugEnabled()).isFalse();
        assertThat(org.slf4j.LoggerFactory.getLogger("org.springframework.http.codec.ServerSentEventHttpMessageWriter").isDebugEnabled()).isFalse();
        String subject="chat-smoke-"+UUID.randomUUID();
        var user=users.saveAndFlush(User.builder().keycloakId(subject).username(subject).email(subject+"@example.invalid").build());
        String id=null;
        try {
            memory.put(subject,"response_detail","concise");
            assertThat(memory.list(subject)).containsEntry("response_detail","concise");
            var conversation=store.create(subject,null); id=conversation.id();
            var request=new ConversationRequests.Send(); request.setClientRequestId(UUID.randomUUID().toString());
            request.setMessage("In one sentence, what does an electrical resistor do?");
            var turn=store.begin(subject,id,request);
            assertThat(turn.request().getApprovedUserPreferences()).containsEntry("response_detail","concise");
            assertThat(turn.request().getProjectId()).isNull();
            stream.stream(turn).blockLast(Duration.ofMinutes(4));
            var restored=store.history(subject,id,0,50).getContent();
            assertThat(restored).hasSize(2);
            assertThat(restored.get(1).content()).isEqualTo(request.getMessage());
            assertThat(store.list(subject,null,"resistor",0,20).getTotalElements()).isEqualTo(1);
            System.out.println("Development chat smoke: persisted messages="+restored.size()+", assistant status="+restored.getFirst().status()+", response characters="+restored.getFirst().content().length());
            assertThat(restored.getFirst().status()).as("Existing provider must complete a persisted response").isEqualTo("completed");
            assertThat(restored.getFirst().content()).isNotBlank();
            assertThat(store.begin(subject,id,request).replay()).isTrue();
        } finally {
            if(id!=null) { store.stop(subject,id); store.delete(subject,id); }
            memory.delete(subject,"response_detail");
            users.deleteById(user.getId());
        }
    }
}
