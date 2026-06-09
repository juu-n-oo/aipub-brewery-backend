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

    /** 프로젝트별 전체 Dockerfile 조회 (MCP 툴 전용). */
    public List<DockerfileResponse> listByProject(String project) {
        return mapper.toResponseList(repository.findByProject(project));
    }

    /**
     * 멤버 조회: 바인딩된 프로젝트(들) 안의 "본인 소유" Dockerfile 만 생성 일시 최신순으로 반환한다.
     * 프로젝트 IN + username 을 AND 로 묶으므로 삭제된 프로젝트의 Dockerfile 은 제외되고, 타인 것은 보이지 않는다.
     *
     * @param projects 프론트가 UserAuthority 로 구한 현재 바인딩 프로젝트 목록(삭제된 프로젝트 제외)
     * @param username 토큰에서 추출한 호출자 본인 이름
     */
    public List<DockerfileResponse> listForUser(List<String> projects, String username) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }
        return mapper.toResponseList(
                repository.findByProjectInAndUsernameOrderByCreatedAtDesc(projects, username));
    }

    /**
     * 관리자 전체 조회: 모든 Dockerfile 을 생성 일시 최신순으로 반환한다.
     * usernameFilter 가 주어지면 해당 사용자 소유만 필터링한다.
     */
    public List<DockerfileResponse> listAll(String usernameFilter) {
        List<Dockerfile> result = (usernameFilter == null || usernameFilter.isBlank())
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByUsernameOrderByCreatedAtDesc(usernameFilter);
        return mapper.toResponseList(result);
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
