-- Dockerfile revision versioning: 변경 이력을 별도 테이블에 보관

-- 1) dockerfile_revisions 테이블 생성
CREATE TABLE IF NOT EXISTS dockerfile_revisions (
    id              BIGSERIAL       PRIMARY KEY,
    dockerfile_id   BIGINT          NOT NULL REFERENCES dockerfiles(id) ON DELETE CASCADE,
    version         INT             NOT NULL,
    content         TEXT            NOT NULL,
    base_image      VARCHAR(512)    NOT NULL,
    message         TEXT,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_revisions_dockerfile_version UNIQUE (dockerfile_id, version)
);

CREATE INDEX IF NOT EXISTS idx_revisions_dockerfile_id ON dockerfile_revisions(dockerfile_id);

-- 2) dockerfiles 에 latest_revision_id 컬럼 추가 (backfill 전이므로 nullable)
ALTER TABLE dockerfiles ADD COLUMN latest_revision_id BIGINT;

-- 3) 기존 데이터 backfill: 현재 content 를 revision v1 으로 이관
INSERT INTO dockerfile_revisions (dockerfile_id, version, content, base_image, message, created_by, created_at)
SELECT id, 1, content, COALESCE(base_image, ''), 'Initial version', username, created_at
  FROM dockerfiles;

-- 4) latest_revision_id 설정
UPDATE dockerfiles d
   SET latest_revision_id = (
       SELECT r.id FROM dockerfile_revisions r
        WHERE r.dockerfile_id = d.id AND r.version = 1
   );

-- 5) NOT NULL + FK 제약 추가
ALTER TABLE dockerfiles ALTER COLUMN latest_revision_id SET NOT NULL;
ALTER TABLE dockerfiles ADD CONSTRAINT fk_dockerfiles_latest_revision
    FOREIGN KEY (latest_revision_id) REFERENCES dockerfile_revisions(id);
