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
