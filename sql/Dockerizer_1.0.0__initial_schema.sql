CREATE TABLE IF NOT EXISTS dockerfiles (
    id              BIGSERIAL       PRIMARY KEY,
    project         VARCHAR(255)    NOT NULL,
    username        VARCHAR(255)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    content         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_dockerfiles_project_username_name UNIQUE (project, username, name)
);

CREATE TABLE IF NOT EXISTS build_context_files (
    id              BIGSERIAL       PRIMARY KEY,
    dockerfile_id   BIGINT          NOT NULL REFERENCES dockerfiles(id) ON DELETE CASCADE,
    file_name       VARCHAR(255)    NOT NULL,
    target_path     VARCHAR(255)    NOT NULL,
    file_size       BIGINT          NOT NULL,
    storage_path    VARCHAR(255)    NOT NULL,
    uploaded_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_build_context_files_dockerfile_id ON build_context_files(dockerfile_id);
