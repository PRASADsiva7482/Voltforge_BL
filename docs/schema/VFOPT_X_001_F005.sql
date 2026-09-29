-- Apply once to the Voltforge MySQL 8 schema.
-- Flyway remains disabled because the original migration history is absent.
-- Additive indexes only: project documents, permissions and existing indexes stay intact.
-- Matching list queries use scalar summaries and keep their existing pagination order.
-- Measured with EXPLAIN ANALYZE on 4,000 projects / 40 owners:
-- owner first page: 100 rows + sort -> 8 rows, no sort;
-- public offset 160: 252 scanned project rows -> 168.
-- Evidence and allocation/REST measurements: Voltforge_UI/docs/VFOPT_X_001_FINDINGS_BACKLOG.json (F005).
-- Inspect information_schema.statistics before rerunning; this patch intentionally fails
-- on duplicate index names. Other deployment databases need this same one-time patch.
ALTER TABLE projects
    ADD INDEX idx_projects_owner_updated (owner_id, updated_at DESC),
    ADD INDEX idx_projects_public_created (is_public, created_at DESC),
    ALGORITHM=INPLACE,
    LOCK=NONE;

-- Optional rollback affects query performance only:
-- ALTER TABLE projects DROP INDEX idx_projects_owner_updated, DROP INDEX idx_projects_public_created;
