-- 빌드 컨텍스트 파일 업로드가 "백엔드 로컬 디스크 저장(build_context_files)" 방식에서
-- "AIPub Volume PVC 직접 업로드(helper Pod exec)" 방식으로 전환되면서, 로컬 디스크 기반
-- BuildContextFile 서브시스템 일체를 제거한다. 해당 테이블은 한 번도 사용되지 않은 채 비어 있었다.
-- (인덱스 idx_build_context_files_dockerfile_id 는 테이블과 함께 삭제됨)
DROP TABLE IF EXISTS build_context_files;
