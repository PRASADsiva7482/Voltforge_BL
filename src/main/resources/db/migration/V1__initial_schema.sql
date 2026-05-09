-- =============================================================================
-- VoltForge Database Schema — V1 Initial Migration
-- =============================================================================

-- ── Users Table ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(36)        NOT NULL,
    keycloak_id     VARCHAR(100)    NOT NULL,
    username        VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(100)    NULL,
    avatar_url      VARCHAR(500)    NULL,
    bio             TEXT            NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER',
    subscription_type VARCHAR(20)   NOT NULL DEFAULT 'FREE',
    account_status  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_keycloak_id (keycloak_id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    INDEX idx_users_role (role),
    INDEX idx_users_subscription_type (subscription_type),
    INDEX idx_users_account_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Projects Table ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS projects (
    id                  VARCHAR(36)        NOT NULL,
    owner_id            VARCHAR(36)        NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    description         TEXT            NULL,
    board_type          VARCHAR(50)     NOT NULL DEFAULT 'ARDUINO_UNO',
    canvas_layout       JSON            NULL,
    component_config    JSON            NULL,
    is_public           BOOLEAN         NOT NULL DEFAULT FALSE,
    fork_count          INT             NOT NULL DEFAULT 0,
    view_count          INT             NOT NULL DEFAULT 0,
    forked_from         VARCHAR(36)        NULL,
    thumbnail_url       VARCHAR(500)    NULL,
    tags                VARCHAR(500)    NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_projects_owner_id (owner_id),
    INDEX idx_projects_board_type (board_type),
    INDEX idx_projects_is_public (is_public),
    INDEX idx_projects_forked_from (forked_from),
    INDEX idx_projects_created_at (created_at DESC),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_projects_forked_from FOREIGN KEY (forked_from) REFERENCES projects(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Code Files Table ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS code_files (
    id              VARCHAR(36)        NOT NULL,
    project_id      VARCHAR(36)        NOT NULL,
    filename        VARCHAR(255)    NOT NULL,
    content         LONGTEXT        NULL,
    language        VARCHAR(50)     NOT NULL DEFAULT 'cpp',
    sort_order      INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_code_files_project_id (project_id),
    UNIQUE KEY uk_code_files_project_filename (project_id, filename),
    CONSTRAINT fk_code_files_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Components Library Table ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS components (
    id                  VARCHAR(36)        NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    category            VARCHAR(50)     NOT NULL,
    type                VARCHAR(50)     NOT NULL,
    description         TEXT            NULL,
    default_properties  JSON            NULL,
    pin_config          JSON            NULL,
    icon_url            VARCHAR(500)    NULL,
    svg_data            TEXT            NULL,
    is_premium          BOOLEAN         NOT NULL DEFAULT FALSE,
    sort_order          INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_components_name_type (name, type),
    INDEX idx_components_category (category),
    INDEX idx_components_type (type),
    INDEX idx_components_is_premium (is_premium)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Subscriptions Table ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscriptions (
    id              VARCHAR(36)        NOT NULL,
    user_id         VARCHAR(36)        NOT NULL,
    plan_type       VARCHAR(20)     NOT NULL DEFAULT 'FREE',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    starts_at       TIMESTAMP       NULL,
    expires_at      TIMESTAMP       NULL,
    payment_ref     VARCHAR(255)    NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_subscriptions_user_id (user_id),
    INDEX idx_subscriptions_plan_type (plan_type),
    INDEX idx_subscriptions_status (status),
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Project Shares Table ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_shares (
    id                  VARCHAR(36)        NOT NULL,
    project_id          VARCHAR(36)        NOT NULL,
    shared_with_user_id VARCHAR(36)        NOT NULL,
    permission          VARCHAR(20)     NOT NULL DEFAULT 'VIEW',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_shares (project_id, shared_with_user_id),
    INDEX idx_project_shares_user (shared_with_user_id),
    CONSTRAINT fk_project_shares_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_shares_user FOREIGN KEY (shared_with_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
