-- Additive development migration. MySQL DDL commits implicitly.
-- No arbitrary text memory, secrets, or project data are accepted.
CREATE TABLE IF NOT EXISTS ai_user_memories (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  preference_key VARCHAR(32) NOT NULL,
  preference_value VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_ai_user_memory UNIQUE (owner_id, preference_key),
  CONSTRAINT fk_ai_user_memory_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_ai_user_memory_value CHECK (
    (preference_key = 'response_detail' AND preference_value IN ('concise', 'detailed')) OR
    (preference_key = 'units' AND preference_value IN ('metric', 'imperial')) OR
    (preference_key = 'experience' AND preference_value IN ('beginner', 'intermediate', 'advanced'))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
