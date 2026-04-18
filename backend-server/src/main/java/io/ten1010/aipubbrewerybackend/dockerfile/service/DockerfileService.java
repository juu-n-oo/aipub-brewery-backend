package io.ten1010.aipubbrewerybackend.dockerfile.service;

import io.ten1010.aipubbrewerybackend.common.exception.ResourceNotFoundException;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileMapper;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.entity.Dockerfile;
import io.ten1010.aipubbrewerybackend.dockerfile.repository.DockerfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DockerfileService {

    private final DockerfileRepository repository;
    private final DockerfileMapper mapper;
    private final DockerfileValidator validator;

    @Transactional
    public DockerfileResponse create(DockerfileCreateRequest request) {
        validator.validate(request.getContent());
        Dockerfile entity = mapper.toEntity(request);
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
    public DockerfileResponse update(Long id, DockerfileUpdateRequest request) {
        validator.validate(request.getContent());
        Dockerfile entity = findById(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        entity.setContent(request.getContent());
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
