package io.ten1010.dockerizerbackend.dockerfile.service;

import io.ten1010.dockerizerbackend.common.exception.ResourceNotFoundException;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileMapper;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.dockerizerbackend.dockerfile.entity.Dockerfile;
import io.ten1010.dockerizerbackend.dockerfile.entity.DockerfileRevision;
import io.ten1010.dockerizerbackend.dockerfile.repository.DockerfileRepository;
import io.ten1010.dockerizerbackend.dockerfile.repository.DockerfileRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DockerfileService {

    private final DockerfileRepository repository;
    private final DockerfileRevisionRepository revisionRepository;
    private final DockerfileMapper mapper;
    private final DockerfileValidator validator;

    @Transactional
    public DockerfileResponse create(DockerfileCreateRequest request, String username) {
        validator.validate(request.getContent());
        Dockerfile entity = mapper.toEntity(request);
        entity.setUsername(username);
        entity = repository.save(entity);

        DockerfileRevision revision = DockerfileRevision.builder()
                .dockerfile(entity)
                .version(1)
                .content(request.getContent())
                .baseImage(request.getBaseImage())
                .message("Initial version")
                .createdBy(username)
                .build();
        revision = revisionRepository.save(revision);

        entity.setLatestRevision(revision);
        return mapper.toResponse(repository.save(entity));
    }

    public DockerfileResponse getById(Long id) {
        return mapper.toResponse(findById(id));
    }

    public List<DockerfileResponse> listByProject(String project) {
        return mapper.toResponseList(repository.findByProject(project));
    }

    public List<DockerfileResponse> listByProjectAndUser(String project, String username) {
        return mapper.toResponseList(repository.findByProjectAndUsername(project, username));
    }

    @Transactional
    public DockerfileResponse update(Long id, DockerfileUpdateRequest request, String username) {
        validator.validate(request.getContent());
        Dockerfile entity = findById(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        entity.setContent(request.getContent());
        entity.setBaseImage(request.getBaseImage());

        int nextVersion = revisionRepository.findTopByDockerfileIdOrderByVersionDesc(id)
                .map(r -> r.getVersion() + 1)
                .orElse(1);

        DockerfileRevision revision = DockerfileRevision.builder()
                .dockerfile(entity)
                .version(nextVersion)
                .content(request.getContent())
                .baseImage(request.getBaseImage())
                .message(request.getMessage())
                .createdBy(username)
                .build();
        revision = revisionRepository.save(revision);

        entity.setLatestRevision(revision);
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Dockerfile not found: " + id);
        }
        repository.deleteById(id);
    }

    private Dockerfile findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dockerfile not found: " + id));
    }

}
