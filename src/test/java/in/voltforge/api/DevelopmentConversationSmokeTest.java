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
    @Autowired Environment environment;
    @Autowired UserRepository users;
    @Autowired ConversationStore store;
    @Autowired ConversationStream stream;
    @Test void realDatabaseAndProviderRoundTrip() {
        assertThat(environment.getProperty("app.ai.environment")).isEqualTo("development");
        String subject="chat-smoke-"+UUID.randomUUID();
        var user=users.saveAndFlush(User.builder().keycloakId(subject).username(subject).email(subject+"@example.invalid").build());
        String id=null;
        try {
            var conversation=store.create(subject,null); id=conversation.id();
            var request=new ConversationRequests.Send(); request.setClientRequestId(UUID.randomUUID().toString());
            request.setMessage("In one sentence, what does an electrical resistor do?");
            var turn=store.begin(subject,id,request); stream.stream(turn).blockLast(Duration.ofMinutes(4));
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
            users.deleteById(user.getId());
        }
    }
}
