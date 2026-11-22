-- WHISLY PostgreSQL Control Plane Schema (10 Required Tables)

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ORG_ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    org_code VARCHAR(64) NOT NULL UNIQUE,
    tier VARCHAR(50) NOT NULL DEFAULT 'ENTERPRISE_OFFLINE',
    max_devices INTEGER NOT NULL DEFAULT 250,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS organization_members (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role VARCHAR(50) NOT NULL DEFAULT 'ORG_ADMIN',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);

CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(36) PRIMARY KEY,
    device_fingerprint VARCHAR(128) NOT NULL UNIQUE,
    organization_id VARCHAR(36) REFERENCES organizations(id) ON DELETE SET NULL,
    app_version VARCHAR(32) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS licenses (
    id VARCHAR(36) PRIMARY KEY,
    license_key VARCHAR(128) NOT NULL UNIQUE,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_id VARCHAR(36) REFERENCES devices(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS language_packs (
    pack_id VARCHAR(128) PRIMARY KEY,
    language_code VARCHAR(16) NOT NULL,
    indic_trans_tag VARCHAR(32) NOT NULL,
    english_name VARCHAR(64) NOT NULL,
    native_name VARCHAR(64) NOT NULL,
    task_type VARCHAR(16) NOT NULL,
    is_shared_resource BOOLEAN NOT NULL DEFAULT FALSE,
    tts_neural_supported BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS model_versions (
    id VARCHAR(36) PRIMARY KEY,
    pack_id VARCHAR(128) NOT NULL REFERENCES language_packs(pack_id) ON DELETE CASCADE,
    version VARCHAR(32) NOT NULL,
    s3_object_key VARCHAR(512) NOT NULL,
    download_url VARCHAR(512) NOT NULL,
    sha256_checksum VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    required_runtime VARCHAR(64) NOT NULL DEFAULT 'ONNX Runtime Mobile >= 1.18',
    min_memory_mb INTEGER NOT NULL DEFAULT 384,
    license_name VARCHAR(128) NOT NULL,
    attribution VARCHAR(255) NOT NULL,
    manifest_signature_hex VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pack_version UNIQUE (pack_id, version)
);

CREATE TABLE IF NOT EXISTS model_releases (
    id VARCHAR(36) PRIMARY KEY,
    release_tag VARCHAR(64) NOT NULL UNIQUE,
    model_version_id VARCHAR(36) NOT NULL REFERENCES model_versions(id) ON DELETE CASCADE,
    channel VARCHAR(32) NOT NULL DEFAULT 'STABLE',
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    release_notes TEXT,
    published_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS correction_submissions (
    id VARCHAR(36) PRIMARY KEY,
    source_lang VARCHAR(16) NOT NULL,
    target_lang VARCHAR(16) NOT NULL,
    original_stt_text TEXT NOT NULL,
    corrected_stt_text TEXT NOT NULL,
    model_translation TEXT NOT NULL,
    corrected_translation TEXT,
    user_consented BOOLEAN NOT NULL CHECK (user_consented = TRUE),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    actor_id VARCHAR(36),
    action VARCHAR(128) NOT NULL,
    target_resource VARCHAR(255) NOT NULL,
    sanitized_metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_language_packs_lang_task ON language_packs(language_code, task_type);
CREATE INDEX IF NOT EXISTS ix_model_versions_pack_version ON model_versions(pack_id, version);
CREATE INDEX IF NOT EXISTS ix_devices_org ON devices(organization_id);
CREATE INDEX IF NOT EXISTS ix_licenses_org ON licenses(organization_id);
CREATE INDEX IF NOT EXISTS ix_audit_logs_action ON audit_logs(action);
