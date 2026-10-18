-- Development migration VF_CHAT_001. Additive and repeatable; MySQL DDL auto-commits.
-- Flyway remains disabled until the repository's earlier migration history is restored.
CREATE TABLE IF NOT EXISTS ai_conversations (
 id VARCHAR(36) NOT NULL,
 owner_id VARCHAR(36) NOT NULL,
 project_id VARCHAR(36) NULL,
 title VARCHAR(200) NOT NULL,
 next_sequence BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id),
 KEY idx_ai_conversations_owner_activity (owner_id, updated_at DESC, id),
 KEY idx_ai_conversations_project (project_id),
 CONSTRAINT fk_ai_conversation_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_ai_conversation_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_messages (
 id VARCHAR(36) NOT NULL,
 conversation_id VARCHAR(36) NOT NULL,
 client_request_id VARCHAR(36) NOT NULL,
 sequence_number BIGINT NOT NULL,
 role VARCHAR(16) NOT NULL,
 status VARCHAR(20) NOT NULL,
 content MEDIUMTEXT NOT NULL,
 metadata JSON NULL,
 attempt_id VARCHAR(36) NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_ai_message_sequence (conversation_id, sequence_number),
 UNIQUE KEY uk_ai_message_request_role (conversation_id, client_request_id, role),
 KEY idx_ai_message_status (conversation_id, status),
 CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
