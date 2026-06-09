package io.ten1010.dockerizerbackend.dockerfile.repository;

import io.ten1010.dockerizerbackend.dockerfile.entity.Dockerfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DockerfileRepository extends JpaRepository<Dockerfile, Long> {

    List<Dockerfile> findByProjectAndUsername(String project, String username);

    List<Dockerfile> findByProject(String project);

    Optional<Dockerfile> findByProjectAndUsernameAndName(String project, String username, String name);

    /**
     * 멤버 조회: 현재 바인딩된 프로젝트 목록 ∩ 본인 소유. 프로젝트 IN + username 을 AND 로 묶어
     * 삭제된 프로젝트의 Dockerfile 은 자동으로 제외되고, 본인 것만 보장된다.
     */
    List<Dockerfile> findByProjectInAndUsernameOrderByCreatedAtDesc(List<String> projects, String username);

    /** 관리자 전체 조회 (생성 일시 최신순). */
    List<Dockerfile> findAllByOrderByCreatedAtDesc();

    /** 관리자 username 필터 조회 (생성 일시 최신순). */
    List<Dockerfile> findByUsernameOrderByCreatedAtDesc(String username);

}
