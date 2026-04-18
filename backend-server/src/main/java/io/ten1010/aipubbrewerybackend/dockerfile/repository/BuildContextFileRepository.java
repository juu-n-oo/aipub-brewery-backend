package io.ten1010.aipubbrewerybackend.dockerfile.repository;

import io.ten1010.aipubbrewerybackend.dockerfile.entity.BuildContextFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildContextFileRepository extends JpaRepository<BuildContextFile, Long> {

    List<BuildContextFile> findByDockerfileId(Long dockerfileId);

}
