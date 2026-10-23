package in.voltforge.api.ai.memory;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Only allowlisted, non-sensitive preferences; never free-text facts or transcripts. */
@Entity @Getter @Setter
@Table(name="ai_user_memories", uniqueConstraints=@UniqueConstraint(name="uk_ai_user_memory", columnNames={"owner_id","preference_key"}))
public class UserMemory extends BaseEntity {
    @Column(name="owner_id", nullable=false, length=36) private String ownerId;
    @Column(name="preference_key", nullable=false, length=32) private String preferenceKey;
    @Column(name="preference_value", nullable=false, length=32) private String preferenceValue;
}
