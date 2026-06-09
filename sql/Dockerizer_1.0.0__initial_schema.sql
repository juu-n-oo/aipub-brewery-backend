-- Dockerizer 초기 스키마 (v1.0.0)
--
-- 기존 증분 마이그레이션 1.0.0~1.4.0 을 단일 초기 스키마로 병합(squash)했다. (2026-06-09, 출시 전)
-- 반영된 최종 상태:
--   - dockerfiles: + base_image(NOT NULL, 1.1.0), + latest_revision_id(nullable FK, 1.2.0/1.4.0)
--   - dockerfile_revisions(1.2.0)
--   - build_context_files 는 제거됨(1.3.0) → 본 스키마에 포함하지 않음
--
-- dockerfiles.latest_revision_id ↔ dockerfile_revisions.dockerfile_id 가 서로를 참조하는
-- 순환 FK 구조다. 따라서 dockerfiles 를 (FK 없이) 먼저 만들고, dockerfile_revisions 생성 후
-- dockerfiles 에 FK 를 ALTER 로 추가한다.
--
-- latest_revision_id 가 nullable 인 이유: 신규 Dockerfile 생성은
--   (1) dockerfiles INSERT (이때 revision 없음 → latest_revision_id = NULL)
--   (2) dockerfile_revisions INSERT
--   (3) dockerfiles UPDATE (latest_revision_id 채움)
-- 순서로 진행되므로, (1) 단계에서 NULL 이 허용되어야 한다. FK 는 NULL 을 허용한다.

CREATE TABLE IF NOT EXISTS dockerfiles (
    id                 BIGSERIAL    PRIMARY KEY,
    project            VARCHAR(255) NOT NULL,
    username           VARCHAR(255) NOT NULL,
    name               VARCHAR(255) NOT NULL,
    description        TEXT,
    content            TEXT         NOT NULL,
    base_image         VARCHAR(512) NOT NULL,
    latest_revision_id BIGINT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_dockerfiles_project_username_name UNIQUE (project, username, name)
);

CREATE TABLE IF NOT EXISTS dockerfile_revisions (
    id              BIGSERIAL    PRIMARY KEY,
    dockerfile_id   BIGINT       NOT NULL REFERENCES dockerfiles(id) ON DELETE CASCADE,
    version         INT          NOT NULL,
    content         TEXT         NOT NULL,
    base_image      VARCHAR(512) NOT NULL,
    message         TEXT,
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_revisions_dockerfile_version UNIQUE (dockerfile_id, version)
);

CREATE INDEX IF NOT EXISTS idx_revisions_dockerfile_id ON dockerfile_revisions(dockerfile_id);

-- 순환 FK: dockerfiles → dockerfile_revisions (재실행 안전하도록 존재 여부 가드)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_dockerfiles_latest_revision'
    ) THEN
        ALTER TABLE dockerfiles
            ADD CONSTRAINT fk_dockerfiles_latest_revision
            FOREIGN KEY (latest_revision_id) REFERENCES dockerfile_revisions(id);
    END IF;
END $$;
