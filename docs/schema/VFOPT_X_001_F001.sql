-- Apply once to the existing Voltforge schema before starting the updated API.
-- Flyway is disabled because migrations 1-4 are absent from this checkout.
-- Do not enable a partial Flyway history to apply this additive patch.
-- Existing documents start at revision 0; project content is unchanged.
ALTER TABLE projects
    ADD COLUMN document_revision BIGINT NOT NULL DEFAULT 0;
