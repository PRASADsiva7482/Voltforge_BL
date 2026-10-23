package in.voltforge.api.ai.document;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="ai_document_versions",uniqueConstraints={
    @UniqueConstraint(columnNames={"document_id","version_number"}), @UniqueConstraint(columnNames={"document_id","content_hash"})}) @Getter @Setter
public class DocumentVersion extends BaseEntity {
    @Column(name="document_id",nullable=false,length=36) private String documentId;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Column(name="content_hash",nullable=false,length=64) private String contentHash;
    @Column(name="media_type",nullable=false,length=50) private String mediaType;
    @Column(name="page_count",nullable=false) private int pageCount;
    @Column(name="chunk_count",nullable=false) private int chunkCount;
    @Column(nullable=false,length=20) private String status;
}
