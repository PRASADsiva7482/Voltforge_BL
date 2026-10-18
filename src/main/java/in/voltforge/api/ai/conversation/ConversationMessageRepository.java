package in.voltforge.api.ai.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, String> {
    Page<ConversationMessage> findByConversationId(String id, Pageable page);
    List<ConversationMessage> findByConversationIdAndStatus(String id, String status);
    Optional<ConversationMessage> findByConversationIdAndClientRequestIdAndRole(String id, String requestId, String role);
    void deleteByConversationId(String id);
}
