package in.voltforge.api.ai.document;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="ai_documents") @Getter @Setter
public class ReferenceDocument extends BaseEntity {
    @Column(name="owner_id",nullable=false,length=36) private String ownerId;
    @Column(name="project_id",length=36) private String projectId;
    @Column(nullable=false,length=200) private String title;
    @Column(name="current_version",nullable=false) private int currentVersion;
}
