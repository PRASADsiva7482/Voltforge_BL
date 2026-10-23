package in.voltforge.api.ai.document;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import java.util.List;

@Entity @Table(name="ai_document_embeddings",uniqueConstraints=@UniqueConstraint(columnNames={"chunk_id","model_id"}))
@Getter @Setter
public class DocumentEmbedding extends BaseEntity {
    @Column(name="chunk_id",nullable=false,length=36) private String chunkId;
    @Column(name="model_id",nullable=false,length=64) private String modelId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="json") private List<Double> embedding;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="chunk_id",insertable=false,updatable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) private DocumentChunk chunk;
}
