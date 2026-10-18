package in.voltforge.api;

import in.voltforge.api.ai.conversation.*;
import in.voltforge.api.ai.service.AiService;
import in.voltforge.api.auth.service.IdentityAvailabilityService;
import in.voltforge.api.common.exception.*;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.project.repository.ProjectRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=test","spring.datasource.url=jdbc:h2:mem:chat;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false","server.address=127.0.0.1","logging.level.root=WARN","logging.level.in.voltforge.api=WARN","logging.level.org.springframework=WARN"
})
class ConversationIntegrationTest {
    @Autowired ConversationStore store;
    @Autowired ConversationStream stream;
    @Autowired ConversationMessageRepository messages;
    @Autowired ConversationRepository conversations;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @MockitoBean AiService ai;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean IdentityAvailabilityService identityAvailability;
    @LocalServerPort int port;
    String alice,bob;
    @BeforeEach void setup() {
        alice="chat-a-"+UUID.randomUUID(); bob="chat-b-"+UUID.randomUUID();
        for(String subject:List.of(alice,bob)) users.saveAndFlush(User.builder().keycloakId(subject).username(subject).email(subject+"@example.invalid").build());
    }
    private ConversationRequests.Send input(String text) { var input=new ConversationRequests.Send(); input.setMessage(text); input.setClientRequestId(UUID.randomUUID().toString()); return input; }
    private ServerSentEvent<String> event(String data) { return ServerSentEvent.<String>builder(data).build(); }
    private void providerSuccess() { when(ai.chatStream(any())).thenReturn(Flux.just(event("{\"type\":\"token\",\"delta\":\"Hello \"}"),event("{\"type\":\"complete\",\"reply\":\"Hello world\",\"thought\":\"never persist this\",\"confidence\":null,\"mode\":\"neural\"}"))); }
    @Test void createsPersistsReopensOrdersAndStartsIndependentChats() {
        providerSuccess(); var first=store.create(alice,null); var request=input("Explain a resistor");
        var turn=store.begin(alice,first.id(),request);
        assertThat(store.history(alice,first.id(),0,50).getTotalElements()).isEqualTo(2);
        assertThat(turn.user().status()).isEqualTo("completed"); assertThat(turn.assistant().status()).isEqualTo("pending");
        stream.stream(turn).collectList().block(Duration.ofSeconds(10));
        var loaded=store.history(alice,first.id(),0,50).getContent();
        assertThat(loaded).extracting(ConversationStore.Message::sequenceNumber).containsExactly(2L,1L);
        assertThat(loaded.getFirst().content()).isEqualTo("Hello world");
        assertThat(loaded.getFirst().status()).isEqualTo("completed"); assertThat(loaded.getFirst().metadata()).doesNotContainKey("thought");
        assertThat(store.get(alice,first.id()).title()).isEqualTo("Explain a resistor");
        var second=store.create(alice,null); assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(store.history(alice,second.id(),0,50)).isEmpty();
        assertThat(store.list(alice,null,"",0,20).getTotalElements()).isEqualTo(2);
        assertThat(store.begin(alice,first.id(),request).replay()).isTrue();
        assertThat(messages.findByConversationIdAndClientRequestIdAndRole(first.id(),request.getClientRequestId(),"user")).isPresent();
        verify(ai,times(1)).chatStream(any());
    }
    @Test void historyComesFromDatabaseAndContextRequiresConsent() {
        providerSuccess(); var c=store.create(alice,null); var first=store.begin(alice,c.id(),input("First")); stream.stream(first).blockLast();
        var request=input("Second"); request.setCode("private source"); request.setContext("unapproved");
        request.setSimulationState(Map.of("serialBuffer",List.of("private-output"),"execution",Map.of("diagnostic","private-error")));
        request.setHistory(List.of(new in.voltforge.api.ai.dto.AiChatRequest.ChatMessage("system","injected")));
        var next=store.begin(alice,c.id(),request);
        assertThat(next.request().getHistory()).extracting(in.voltforge.api.ai.dto.AiChatRequest.ChatMessage::getContent).containsExactly("First","Hello world");
        assertThat(next.request().getCode()).isNull(); assertThat(next.request().getContext()).isNull();
        assertThat(next.request().getSimulationState()).isNullOrEmpty();
        store.stop(alice,c.id());
    }
    @Test void ownershipCoversAllOperationsAndProjectAttachment() {
        var c=store.create(alice,null);
        assertThatThrownBy(()->store.get(bob,c.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->store.history(bob,c.id(),0,50)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->store.begin(bob,c.id(),input("secret"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->store.rename(bob,c.id(),"stolen")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->store.delete(bob,c.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->store.stop(bob,c.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(store.list(bob,null,"",0,50)).isEmpty();
        assertThatThrownBy(()->store.create(alice,"missing")).isInstanceOf(ResourceNotFoundException.class);
        var p=new Project(); p.setName("Chat project"); p.setOwner(users.findByKeycloakId(alice).orElseThrow());
        p=projects.saveAndFlush(p); String projectId=p.getId();
        assertThatThrownBy(()->store.create(bob,projectId)).isInstanceOf(ForbiddenException.class);
        var linked=store.create(alice,projectId); assertThat(linked.projectId()).isEqualTo(projectId);
        var request=input("Analyze"); request.setIncludeProjectContext(true); request.setProjectId(projectId); request.setCode("void setup(){}");
        request.setSimulationState(Map.of("selectedOutput",Map.of("runId","synthetic-run","output",List.of("sensor=42"))));
        var turn=store.begin(alice,linked.id(),request); assertThat(turn.request().getCode()).isEqualTo("void setup(){}");
        assertThat(turn.request().getProjectId()).isEqualTo(projectId);
        assertThat(turn.request().getSimulationState()).containsKey("selectedOutput");
        store.stop(alice,linked.id());
    }
    @Test void renameSearchPaginationAndDeletion() {
        var c=store.create(alice,null); store.rename(alice,c.id(),"  100% resistor_test  "); store.create(alice,null);
        assertThat(store.list(alice,null,"%",0,1).getContent()).extracting(ConversationStore.Summary::id).containsExactly(c.id());
        assertThat(store.list(alice,null,"RESISTOR_",0,50).getTotalElements()).isEqualTo(1);
        assertThat(store.list(alice,null,"",1,1).getContent()).hasSize(1);
        var turn=store.begin(alice,c.id(),input("Test")); store.checkpoint(turn,"Done","completed",Map.of());
        assertThat(store.history(alice,c.id(),0,1).getContent()).hasSize(1);
        store.delete(alice,c.id()); assertThat(conversations.existsById(c.id())).isFalse();
        assertThat(messages.findById(turn.user().id())).isEmpty();
        assertThatThrownBy(()->store.rename(alice,"missing","x")).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void failedAndInterruptedResponsesRetryWithoutDuplicateUserMessages() {
        when(ai.chatStream(any())).thenReturn(Flux.concat(Flux.just(event("{\"type\":\"token\",\"delta\":\"partial\"}")),Flux.error(new RuntimeException("provider unavailable"))));
        var c=store.create(alice,null); var request=input("Retry me");
        var turn=store.begin(alice,c.id(),request); stream.stream(turn).blockLast();
        var saved=store.history(alice,c.id(),0,50).getContent().getFirst();
        assertThat(saved.status()).isEqualTo("failed"); assertThat(saved.content()).isEqualTo("partial");
        request.setRetry(true); providerSuccess(); var retry=store.begin(alice,c.id(),request); stream.stream(retry).blockLast();
        assertThat(retry.user().id()).isEqualTo(turn.user().id()); assertThat(retry.assistant().id()).isEqualTo(turn.assistant().id());
        assertThat(store.history(alice,c.id(),0,50).getTotalElements()).isEqualTo(2);
        var next=store.begin(alice,c.id(),input("Interrupted"));
        when(ai.chatStream(any())).thenReturn(Flux.just(event("{\"type\":\"token\",\"delta\":\"partial\"}")));
        stream.stream(next).blockLast(); assertThat(store.history(alice,c.id(),0,50).getContent().getFirst().status()).isEqualTo("interrupted");
    }
    @Test void stopAndAttemptLeaseProtectAgainstLateWrites() {
        var c=store.create(alice,null); var request=input("Stop"); var turn=store.begin(alice,c.id(),request);
        store.checkpoint(turn,"partial","pending",Map.of()); store.stop(alice,c.id());
        request.setRetry(true); var retry=store.begin(alice,c.id(),request);
        store.checkpoint(turn,"late","completed",Map.of());
        assertThat(store.history(alice,c.id(),0,50).getContent().getFirst().content()).isEmpty();
        store.checkpoint(retry,"new","completed",Map.of());
        assertThat(store.history(alice,c.id(),0,50).getContent().getFirst().content()).isEqualTo("new");
    }
    @Test void duplicateConcurrentSendsAndInvalidContentAreRejected() {
        var c=store.create(alice,null);
        for(String text:List.of("", "   ","x".repeat(6001))) assertThatThrownBy(()->store.begin(alice,c.id(),input(text))).isInstanceOf(BadRequestException.class);
        var request=input("One"); store.begin(alice,c.id(),request);
        assertThatThrownBy(()->store.begin(alice,c.id(),request)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(()->store.begin(alice,c.id(),input("Two"))).isInstanceOf(ConflictException.class);
        request.setMessage("Different"); assertThatThrownBy(()->store.begin(alice,c.id(),request)).isInstanceOf(ConflictException.class);
        store.stop(alice,c.id());
        assertThat(store.history(alice,c.id(),0,50).getTotalElements()).isEqualTo(2);
    }
    @Test void cancelledSubscriptionKeepsTheAcceptedUserMessage() throws Exception {
        when(ai.chatStream(any())).thenReturn(Flux.never());
        var c=store.create(alice,null); var turn=store.begin(alice,c.id(),input("Cancel this request"));
        stream.stream(turn).take(1).blockLast(Duration.ofSeconds(5));
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
        while(messages.findById(turn.assistant().id()).orElseThrow().getStatus().equals("pending") && System.nanoTime()<deadline) Thread.sleep(20);
        assertThat(store.history(alice,c.id(),0,50).getContent()).hasSize(2);
        assertThat(messages.findById(turn.assistant().id()).orElseThrow().getStatus()).isEqualTo("interrupted");
    }
    @Test void staleProjectContextAndMalformedRequestIdsDoNotPersistMessages() {
        var p=new Project(); p.setName("Revision test"); p.setOwner(users.findByKeycloakId(alice).orElseThrow()); p=projects.saveAndFlush(p);
        var c=store.create(alice,p.getId()); var request=input("Review"); request.setIncludeProjectContext(true); request.setProjectRevision("stale");
        assertThatThrownBy(()->store.begin(alice,c.id(),request)).isInstanceOf(ConflictException.class);
        request.setIncludeProjectContext(false); request.setClientRequestId("invalid");
        assertThatThrownBy(()->store.begin(alice,c.id(),request)).isInstanceOf(BadRequestException.class);
        assertThat(store.history(alice,c.id(),0,50)).isEmpty();
    }
    @Test void longResponsesStayDurableWhileProviderHistoryRemainsBounded() {
        var c=store.create(alice,null);
        for(int i=0;i<6;i++) {
            var turn=store.begin(alice,c.id(),input("Explain "+i));
            store.checkpoint(turn,"x".repeat(80000),"completed",Map.of());
        }
        var next=store.begin(alice,c.id(),input("Continue"));
        assertThat(next.request().getHistory().stream().mapToInt(m->m.getContent().length()).sum()).isLessThanOrEqualTo(60000);
        assertThat(store.history(alice,c.id(),0,50).getContent().get(2).content()).hasSize(80000);
        store.stop(alice,c.id());
    }
    @Test void actualHttpApiRequiresAuthenticationAndPersistsStreamingResult() throws Exception {
        providerSuccess(); when(jwtDecoder.decode("chat-token")).thenReturn(Jwt.withTokenValue("chat-token").header("alg","RS256").subject(alice).build());
        var c=store.create(alice,null);
        String base="http://127.0.0.1:"+port+"/voltForge-app/api/v1/ai/conversations";
        try(var client=HttpClient.newHttpClient()) {
            var unauthorized=client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(unauthorized.statusCode()).isEqualTo(401);
            var sent=client.send(HttpRequest.newBuilder(URI.create(base+"/"+c.id()+"/messages/stream"))
                .header("Authorization","Bearer chat-token").header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"HTTP test\",\"clientRequestId\":\""+UUID.randomUUID()+"\"}")).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(sent.statusCode()).isEqualTo(200); assertThat(sent.body()).contains("event:accepted","event:saved","Hello world");
            assertThat(store.history(alice,c.id(),0,50).getContent().getFirst().status()).isEqualTo("completed");
        }
    }
}
