package in.voltforge.api.ai.conversation;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

@Entity @Table(name = "ai_messages", uniqueConstraints = {
    @UniqueConstraint(name = "uk_ai_message_sequence", columnNames = {"conversation_id", "sequence_number"}),
    @UniqueConstraint(name = "uk_ai_message_request_role", columnNames = {"conversation_id", "client_request_id", "role"})
})
@Getter @Setter
public class ConversationMessage extends BaseEntity {
    @Column(name = "conversation_id", nullable = false, length = 36) private String conversationId;
    @Column(name = "client_request_id", nullable = false, length = 36) private String clientRequestId;
    @Column(name = "sequence_number", nullable = false) private long sequenceNumber;
    @Column(nullable = false, length = 16) private String role;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT") private String content;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "json") private Map<String, Object> metadata;
    @Column(name = "attempt_id", length = 36) private String attemptId;
}
