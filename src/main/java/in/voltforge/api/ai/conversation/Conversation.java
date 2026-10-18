package in.voltforge.api.ai.conversation;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "ai_conversations", indexes = @Index(name = "idx_ai_conversations_owner_activity", columnList = "owner_id,updated_at,id"))
@Getter @Setter
public class Conversation extends BaseEntity {
    @Column(name = "owner_id", nullable = false, length = 36) private String ownerId;
    @Column(name = "project_id", length = 36) private String projectId;
    @Column(nullable = false, length = 200) private String title;
    @Column(name = "next_sequence", nullable = false) private long nextSequence;
}
