package io.ten1010.dockerizerbackend.dockerfile.repository;

import io.ten1010.dockerizerbackend.dockerfile.entity.Dockerfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DockerfileRepository extends JpaRepository<Dockerfile, Long> {

    List<Dockerfile> findByProjectAndUsername(String project, String username);

    List<Dockerfile> findByProject(String project);

    Optional<Dockerfile> findByProjectAndUsernameAndName(String project, String username, String name);

}
