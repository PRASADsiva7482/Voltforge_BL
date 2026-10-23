-- Additive, repeatable derived-vector storage. MySQL DDL commits implicitly.
-- Source documents remain authoritative; model changes use a distinct model_id.
CREATE TABLE IF NOT EXISTS ai_document_embeddings (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 chunk_id VARCHAR(36) NOT NULL,
 model_id VARCHAR(64) NOT NULL,
 embedding JSON NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_ai_embedding_chunk_model (chunk_id, model_id),
 CONSTRAINT fk_ai_embedding_chunk FOREIGN KEY (chunk_id) REFERENCES ai_document_chunks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
