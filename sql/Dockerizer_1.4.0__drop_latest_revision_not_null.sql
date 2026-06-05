-- latest_revision_id NOT NULL 제약 해제
--
-- 배경:
--   dockerfiles.latest_revision_id ↔ dockerfile_revisions.dockerfile_id 가 서로를 FK 로 참조하는
--   순환 구조다. 신규 Dockerfile 생성은 반드시 다음 순서로 진행된다:
--     1) dockerfiles INSERT  (id 확보) — 이 시점엔 revision 이 없어 latest_revision_id = NULL
--     2) dockerfile_revisions INSERT (1)에서 얻은 dockerfile_id 사용)
--     3) dockerfiles UPDATE  (latest_revision_id 채움)
--   1.2.0 에서 latest_revision_id 에 SET NOT NULL 을 건 탓에 (1) 단계가 NOT NULL 위반으로 실패하여
--   신규 생성이 전면 불가능했다. NOT NULL 은 deferrable 이 아니므로 커밋 시점 검사로 우회할 수 없다.
--   JPA 엔티티(Dockerfile.latestRevision)도 nullable join column 으로 매핑되어 있어 nullable 이 정합이다.
--
-- 효과:
--   - 신규 생성 시 (1) INSERT 가 성공하고, 같은 트랜잭션의 (3) UPDATE 에서 값이 채워진다.
--   - FK 제약(fk_dockerfiles_latest_revision)은 그대로 유지된다 (FK 는 NULL 을 허용).

ALTER TABLE dockerfiles ALTER COLUMN latest_revision_id DROP NOT NULL;
