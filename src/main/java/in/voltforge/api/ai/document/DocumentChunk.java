package in.voltforge.api.ai.document;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="ai_document_chunks",uniqueConstraints=@UniqueConstraint(columnNames={"version_id","ordinal"})) @Getter @Setter
public class DocumentChunk extends BaseEntity {
    @Column(name="version_id",nullable=false,length=36) private String versionId;
    @Column(nullable=false) private int ordinal;
    @Column(name="page_number",nullable=false) private int pageNumber;
    @Column(nullable=false,length=2000) private String content;
}
