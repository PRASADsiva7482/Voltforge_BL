package in.voltforge.api.ai.conversation;

import in.voltforge.api.ai.dto.AiChatRequest;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

public final class ConversationRequests {
    public record Create(@Size(max = 36) String projectId) {}
    public record Rename(@NotBlank @Size(max = 200) String title) {}
    @Getter @Setter
    public static class Send extends AiChatRequest {
        @NotBlank @Pattern(regexp = "[a-fA-F0-9-]{36}") private String clientRequestId;
        private boolean retry;
        private boolean includeProjectContext;
        @Override public String toString() { return "ConversationSend[private content omitted]"; }
    }
}
