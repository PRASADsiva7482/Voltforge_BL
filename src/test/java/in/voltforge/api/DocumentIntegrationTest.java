package in.voltforge.api;

import in.voltforge.api.ai.document.*;
import in.voltforge.api.ai.conversation.*;
import in.voltforge.api.ai.dto.AiResearchRequest;
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
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=test","app.ai.documents.hybrid-enabled=false","spring.datasource.url=jdbc:h2:mem:documents;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false","server.address=127.0.0.1","logging.level.root=WARN","logging.level.org.springframework=WARN"
})
class DocumentIntegrationTest {
    @Autowired DocumentService documents;
    @Autowired DocumentExtractor extractor;
    @Autowired DocumentVersionRepository versions;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationStore conversations;
    @Autowired ConversationStream stream;
    @MockitoBean AiService ai;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean IdentityAvailabilityService identityAvailability;
    @LocalServerPort int port;
    String alice,bob;
    @BeforeEach void setup() {
        alice="document-a-"+UUID.randomUUID(); bob="document-b-"+UUID.randomUUID();
        for(String name:List.of(alice,bob)) users.saveAndFlush(User.builder().keycloakId(name).username(name).email(name+"@example.invalid").build());
    }
    DocumentExtractor.Extracted text(String text) { return extractor.extract(text.getBytes(StandardCharsets.UTF_8),"reference.txt"); }
    @Test void dedupVersionsLexicalSearchIsolationAndDeletion() {
        var doc=documents.save(alice,null,null,"Synthetic datasheet",text("ZX123 voltage 3.3 V. Unrelated current specifications."));
        assertThat(documents.save(alice,null,null,"Duplicate",text("ZX123 voltage 3.3 V. Unrelated current specifications.")).id()).isEqualTo(doc.id());
        assertThat(documents.list(alice,null,"datasheet",0).getTotalElements()).isEqualTo(1);
        assertThat(documents.list(bob,null,"",0)).isEmpty();
        var sources=documents.retrieve(alice,null,List.of(doc.id()),"ZX123 voltage");
        var source=sources.getFirst();
        assertThat(source.get("excerpt")).asString().contains("3.3 V");
        assertThatThrownBy(()->documents.retrieve(bob,null,List.of(doc.id()),"ZX123")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->documents.retrieve(alice,null,List.of(doc.id()),"unfindablepart789")).isInstanceOf(BadRequestException.class);
        var updated=documents.save(alice,null,doc.id(),"Updated datasheet",text("ZX123 voltage 5 V."));
        assertThat(updated.currentVersion()).isEqualTo(2);
        assertThat(documents.retrieve(alice,null,List.of(doc.id()),"ZX123").getFirst().get("excerpt")).asString().contains("5 V");
        assertThat(documents.readChunk(alice,doc.id(),(String)source.get("versionId"),(String)source.get("sourceId")).get("content")).asString().contains("3.3 V");
        assertThatThrownBy(()->documents.readChunk(bob,doc.id(),(String)source.get("versionId"),(String)source.get("sourceId"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->documents.delete(bob,doc.id())).isInstanceOf(ResourceNotFoundException.class);
        documents.delete(alice,doc.id());
        assertThat(versions.findByDocumentId(doc.id())).isEmpty();
        assertThatThrownBy(()->documents.readChunk(alice,doc.id(),(String)source.get("versionId"),(String)source.get("sourceId"))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void projectDocumentsRequireOwningProjectAndExplicitContext() {
        var owner=users.findByKeycloakId(alice).orElseThrow();
        var p=new Project(); p.setOwner(owner); p.setName("Document scope"); p.setBoardType(in.voltforge.api.common.enums.BoardType.ARDUINO_UNO); projects.saveAndFlush(p);
        assertThatThrownBy(()->documents.save(bob,p.getId(),null,"Denied",text("ZX123"))).isInstanceOf(ForbiddenException.class);
        var doc=documents.save(alice,p.getId(),null,"Project reference",text("ZX123 voltage 3.3 V"));
        assertThat(documents.list(alice,null,"",0)).isEmpty();
        assertThatThrownBy(()->documents.retrieve(alice,null,List.of(doc.id()),"ZX123")).isInstanceOf(ForbiddenException.class);
        var conversation=conversations.create(alice,p.getId());
        var send=new ConversationRequests.Send(); send.setMessage("ZX123 voltage"); send.setClientRequestId(UUID.randomUUID().toString());
        send.setResearch(new AiResearchRequest(null,null,false,List.of(doc.id())));
        assertThatThrownBy(()->conversations.begin(alice,conversation.id(),send)).isInstanceOf(ForbiddenException.class);
        send.setIncludeProjectContext(true); send.setProjectId(p.getId());
        var turn=conversations.begin(alice,conversation.id(),send);
        assertThat(turn.request().getAuthorizedDocumentEvidence()).hasSize(1);
        assertThat(turn.request().getResearch().mode()).isEqualTo(AiResearchRequest.Mode.off);
    }
    @Test void documentEvidencePersistsInTheExistingConversationStream() {
        var doc=documents.save(alice,null,null,"Reference",text("ZX123 voltage is 3.3 V"));
        var c=conversations.create(alice,null); var send=new ConversationRequests.Send(); send.setMessage("ZX123 voltage"); send.setClientRequestId(UUID.randomUUID().toString());
        send.setResearch(new AiResearchRequest(null,null,false,List.of(doc.id())));
        send.setAuthorizedDocumentEvidence(List.of(Map.of("excerpt","forged")));
        var turn=conversations.begin(alice,c.id(),send);
        assertThat(turn.request().getAuthorizedDocumentEvidence().getFirst().get("excerpt")).asString().contains("3.3 V");
        var citation=new LinkedHashMap<>(turn.request().getAuthorizedDocumentEvidence().getFirst()); citation.put("citationId","S1");
        String json=new tools.jackson.databind.json.JsonMapper().writeValueAsString(Map.of("type","complete","reply","3.3 V [S1]","citations",List.of(citation)));
        when(ai.chatStream(any())).thenReturn(reactor.core.publisher.Flux.just(org.springframework.http.codec.ServerSentEvent.<String>builder(json).build()));
        stream.stream(turn).blockLast();
        assertThat(conversations.history(alice,c.id(),0,50).getContent().getFirst().metadata()).containsKey("citations");
        assertThat(conversations.begin(alice,c.id(),send).replay()).isTrue();
        assertThat(conversations.history(alice,c.id(),0,50).getTotalElements()).isEqualTo(2);
    }
    @Test void extractionRejectsUnsupportedScansAndKeepsPdfPageNumbers() throws Exception {
        assertThatThrownBy(()->extractor.extract(new byte[0],"empty.txt")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->extractor.extract(new byte[DocumentExtractor.MAX_BYTES+1],"large.txt")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->extractor.extract(new byte[]{(byte)0xff},"invalid.txt")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->extractor.extract("<html>".getBytes(),"page.html")).isInstanceOf(BadRequestException.class);
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new ByteArrayOutputStream()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage()); pdf.save(out);
            assertThatThrownBy(()->extractor.extract(out.toByteArray(),"scan.pdf")).isInstanceOf(BadRequestException.class).hasMessageContaining("OCR");
            var page=new org.apache.pdfbox.pdmodel.PDPage(); pdf.addPage(page);
            try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)) {
                content.beginText(); content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12); content.newLineAtOffset(20,700); content.showText("ZX123 supply voltage 3.3 V"); content.endText();
            }
            out.reset(); pdf.save(out);
            assertThat(extractor.extract(out.toByteArray(),"reference.pdf").chunks().getFirst().page()).isEqualTo(2);
        }
    }
    @Test void authenticatedMultipartUploadAndSourceAccess() throws Exception {
        when(jwtDecoder.decode("document-token")).thenReturn(Jwt.withTokenValue("document-token").header("alg","RS256").subject(alice).build());
        String base="http://127.0.0.1:"+port+"/voltForge-app/api/v1/ai/documents";
        try(var client=HttpClient.newHttpClient()) {
            assertThat(client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            String body="--vf-boundary\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nHTTP reference\r\n--vf-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"ref.txt\"\r\nContent-Type: text/plain\r\n\r\nZX123 voltage 3.3 V\r\n--vf-boundary--\r\n";
            var response=client.send(HttpRequest.newBuilder(URI.create(base)).header("Authorization","Bearer document-token").header("Content-Type","multipart/form-data; boundary=vf-boundary").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(201); assertThat(response.body()).contains("HTTP reference");
        }
    }
}
