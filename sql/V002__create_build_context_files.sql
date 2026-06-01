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
