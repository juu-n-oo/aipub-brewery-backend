package io.ten1010.aipubbrewerybackend.dockerfile.repository;

import io.ten1010.aipubbrewerybackend.dockerfile.entity.BuildContextFile;
import io.ten1010.aipubbrewerybackend.dockerfile.entity.Dockerfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DockerfileRepositoryTest {

    @Autowired
    private DockerfileRepository dockerfileRepository;

    @Autowired
    private BuildContextFileRepository buildContextFileRepository;

    private Dockerfile createAndSaveDockerfile(String project, String username, String name) {
        Dockerfile dockerfile = Dockerfile.builder()
                .project(project)
                .username(username)
                .name(name)
                .content("FROM ubuntu:22.04\nRUN apt-get update")
                .build();
        return dockerfileRepository.save(dockerfile);
    }

    @Test
    void saveAndFindById() {
        Dockerfile saved = createAndSaveDockerfile("pjw", "joonwoo", "test-dockerfile");

        Optional<Dockerfile> found = dockerfileRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getProject()).isEqualTo("pjw");
        assertThat(found.get().getUsername()).isEqualTo("joonwoo");
        assertThat(found.get().getName()).isEqualTo("test-dockerfile");
        assertThat(found.get().getContextFiles()).isEmpty();
    }

    @Test
    void findByProject() {
        createAndSaveDockerfile("pjw", "user1", "dockerfile-1");
        createAndSaveDockerfile("pjw", "user2", "dockerfile-2");
        createAndSaveDockerfile("other", "user3", "dockerfile-3");

        List<Dockerfile> results = dockerfileRepository.findByProject("pjw");

        assertThat(results).hasSize(2);
    }

    @Test
    void findByProjectAndUsername() {
        createAndSaveDockerfile("pjw", "joonwoo", "df-1");
        createAndSaveDockerfile("pjw", "joonwoo", "df-2");
        createAndSaveDockerfile("pjw", "other", "df-3");

        List<Dockerfile> results = dockerfileRepository.findByProjectAndUsername("pjw", "joonwoo");

        assertThat(results).hasSize(2);
    }

    @Test
    void findByProjectAndUsernameAndName() {
        createAndSaveDockerfile("pjw", "joonwoo", "pytorch-cuda12");

        Optional<Dockerfile> result = dockerfileRepository.findByProjectAndUsernameAndName("pjw", "joonwoo", "pytorch-cuda12");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("pytorch-cuda12");
    }

    @Test
    void saveDockerfileWithContextFiles() {
        Dockerfile dockerfile = createAndSaveDockerfile("pjw", "joonwoo", "with-files");

        BuildContextFile file = BuildContextFile.builder()
                .dockerfile(dockerfile)
                .fileName("requirements.txt")
                .targetPath("requirements.txt")
                .fileSize(256L)
                .storagePath("/storage/pjw/dockerfiles/" + dockerfile.getId() + "/requirements.txt")
                .build();
        dockerfile.getContextFiles().add(file);
        dockerfileRepository.save(dockerfile);

        Dockerfile found = dockerfileRepository.findById(dockerfile.getId()).orElseThrow();
        assertThat(found.getContextFiles()).hasSize(1);
        assertThat(found.getContextFiles().getFirst().getFileName()).isEqualTo("requirements.txt");
        assertThat(found.getContextFiles().getFirst().getFileSize()).isEqualTo(256L);
    }

    @Test
    void deleteDockerfileCascadesContextFiles() {
        Dockerfile dockerfile = createAndSaveDockerfile("pjw", "joonwoo", "cascade-test");

        BuildContextFile file = BuildContextFile.builder()
                .dockerfile(dockerfile)
                .fileName("app.py")
                .targetPath("app.py")
                .fileSize(1024L)
                .storagePath("/storage/pjw/dockerfiles/" + dockerfile.getId() + "/app.py")
                .build();
        dockerfile.getContextFiles().add(file);
        dockerfileRepository.save(dockerfile);

        Long dockerfileId = dockerfile.getId();
        dockerfileRepository.deleteById(dockerfileId);

        assertThat(dockerfileRepository.findById(dockerfileId)).isEmpty();
        assertThat(buildContextFileRepository.findByDockerfileId(dockerfileId)).isEmpty();
    }

    @Test
    void timestampsAreAutoPopulated() {
        Dockerfile saved = createAndSaveDockerfile("pjw", "joonwoo", "timestamp-test");

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

}
