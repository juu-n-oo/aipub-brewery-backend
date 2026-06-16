package io.ten1010.imagekitbackend.dockerfile.service;

import io.ten1010.imagekitbackend.common.exception.ResourceNotFoundException;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileMapper;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileRevisionResponse;
import io.ten1010.imagekitbackend.dockerfile.entity.Dockerfile;
import io.ten1010.imagekitbackend.dockerfile.entity.DockerfileRevision;
import io.ten1010.imagekitbackend.dockerfile.repository.DockerfileRepository;
import io.ten1010.imagekitbackend.dockerfile.repository.DockerfileRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DockerfileRevisionService {

    private final DockerfileRepository dockerfileRepository;
    private final DockerfileRevisionRepository revisionRepository;
    private final DockerfileMapper mapper;

    public List<DockerfileRevisionResponse> listRevisions(Long dockerfileId) {
        if (!dockerfileRepository.existsById(dockerfileId)) {
            throw new ResourceNotFoundException("Dockerfile not found: " + dockerfileId);
        }
        return mapper.toRevisionResponseList(
                revisionRepository.findByDockerfileIdOrderByVersionDesc(dockerfileId));
    }

    public DockerfileRevisionResponse getRevision(Long dockerfileId, int version) {
        return revisionRepository.findByDockerfileIdAndVersion(dockerfileId, version)
                .map(mapper::toRevisionResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Revision not found: dockerfile=" + dockerfileId + ", version=" + version));
    }

    @Transactional
    public DockerfileResponse rollback(Long dockerfileId, int version, String username) {
        Dockerfile dockerfile = dockerfileRepository.findById(dockerfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Dockerfile not found: " + dockerfileId));

        DockerfileRevision targetRevision = revisionRepository.findByDockerfileIdAndVersion(dockerfileId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Revision not found: dockerfile=" + dockerfileId + ", version=" + version));

        int nextVersion = revisionRepository.findTopByDockerfileIdOrderByVersionDesc(dockerfileId)
                .map(r -> r.getVersion() + 1)
                .orElse(1);

        DockerfileRevision newRevision = DockerfileRevision.builder()
                .dockerfile(dockerfile)
                .version(nextVersion)
                .content(targetRevision.getContent())
                .baseImage(targetRevision.getBaseImage())
                .message("Rollback to v" + version)
                .createdBy(username)
                .build();
        newRevision = revisionRepository.save(newRevision);

        dockerfile.setContent(targetRevision.getContent());
        dockerfile.setBaseImage(targetRevision.getBaseImage());
        dockerfile.setLatestRevision(newRevision);

        return mapper.toResponse(dockerfileRepository.save(dockerfile));
    }

}
