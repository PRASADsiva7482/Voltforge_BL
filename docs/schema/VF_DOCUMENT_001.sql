-- Additive development migration. MySQL DDL commits implicitly; snapshot first.
CREATE TABLE IF NOT EXISTS ai_documents (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 owner_id VARCHAR(36) NOT NULL,
 project_id VARCHAR(36) NULL,
 title VARCHAR(200) NOT NULL,
 current_version INT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 KEY idx_ai_document_owner (owner_id, updated_at, id),
 KEY idx_ai_document_project (project_id),
 CONSTRAINT fk_ai_document_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_ai_document_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_document_versions (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 document_id VARCHAR(36) NOT NULL,
 version_number INT NOT NULL,
 content_hash VARCHAR(64) NOT NULL,
 media_type VARCHAR(50) NOT NULL,
 page_count INT NOT NULL,
 chunk_count INT NOT NULL,
 status VARCHAR(20) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_ai_document_version (document_id, version_number),
 UNIQUE KEY uk_ai_document_hash (document_id, content_hash),
 CONSTRAINT fk_ai_document_version FOREIGN KEY (document_id) REFERENCES ai_documents(id) ON DELETE CASCADE,
 CONSTRAINT ck_ai_document_ready CHECK (status = 'ready' AND chunk_count > 0 AND page_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_document_chunks (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 version_id VARCHAR(36) NOT NULL,
 ordinal INT NOT NULL,
 page_number INT NOT NULL,
 content VARCHAR(2000) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_ai_document_chunk (version_id, ordinal),
 CONSTRAINT fk_ai_document_chunk FOREIGN KEY (version_id) REFERENCES ai_document_versions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
