package io.ten1010.dockerizerbackend.dockerfile.repository;

import io.ten1010.dockerizerbackend.dockerfile.entity.DockerfileRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DockerfileRevisionRepository extends JpaRepository<DockerfileRevision, Long> {

    List<DockerfileRevision> findByDockerfileIdOrderByVersionDesc(Long dockerfileId);

    Optional<DockerfileRevision> findByDockerfileIdAndVersion(Long dockerfileId, int version);

    Optional<DockerfileRevision> findTopByDockerfileIdOrderByVersionDesc(Long dockerfileId);

}
