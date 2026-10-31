package in.voltforge.api;

import in.voltforge.api.ai.gateway.AiGatewayException;
import in.voltforge.api.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConversationPreflightErrorTest {
    @RestController static class FailingPreflight {
        @PostMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
        String send() {
            throw new AiGatewayException(HttpStatus.SERVICE_UNAVAILABLE,"AI_EMBEDDING_UNAVAILABLE",
                "Reference indexing is unavailable or busy. Retry shortly; your documents are preserved.",true);
        }
        @PostMapping(value="/stream/{kind}",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
        String reject(@PathVariable String kind) {
            throw switch(kind) {
                case "FORBIDDEN" -> new in.voltforge.api.common.exception.ForbiddenException("Project sharing required");
                case "BAD_REQUEST" -> new in.voltforge.api.common.exception.BadRequestException("Invalid context");
                case "CHAT_REQUEST_REUSED" -> new in.voltforge.api.common.exception.ConflictException(kind,"Request already used");
                default -> new in.voltforge.api.common.exception.ResourceNotFoundException("Conversation","id","synthetic");
            };
        }
    }
    @ParameterizedTest @ValueSource(strings={"FORBIDDEN","BAD_REQUEST","CHAT_REQUEST_REUSED","RESOURCE_NOT_FOUND"})
    void preservesPreflightStatusAndCode(String kind) throws Exception {
        int code=switch(kind) { case "FORBIDDEN" -> 403; case "BAD_REQUEST" -> 400; case "CHAT_REQUEST_REUSED" -> 409; default -> 404; };
        MockMvcBuilders.standaloneSetup(new FailingPreflight()).setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(post("/stream/"+kind).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().is(code)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorCode").value(kind));
    }
    @Test void returnsStructuredFailureBeforeStreamStartsEvenWhenClientAcceptsOnlySse() throws Exception {
        MockMvcBuilders.standaloneSetup(new FailingPreflight()).setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(post("/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isServiceUnavailable()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorCode").value("AI_EMBEDDING_UNAVAILABLE"));
    }
}
