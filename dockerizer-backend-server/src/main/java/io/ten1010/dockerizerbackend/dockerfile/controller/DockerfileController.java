package io.ten1010.dockerizerbackend.dockerfile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileRevisionResponse;
import io.ten1010.dockerizerbackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.dockerizerbackend.dockerfile.service.DockerfileRevisionService;
import io.ten1010.dockerizerbackend.dockerfile.service.DockerfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1alpha1/dockerfiles")
@RequiredArgsConstructor
@Tag(name = "Dockerfile", description = "Dockerfile 관리 (CRUD) 및 리비전 이력")
public class DockerfileController {

    private final DockerfileService service;
    private final DockerfileRevisionService revisionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dockerfile 생성", description = "새 Dockerfile을 생성한다. COPY는 허용되며, ADD 지시자가 포함된 경우 reject된다.")
    public DockerfileResponse create(@Valid @RequestBody DockerfileCreateRequest request,
                                     Authentication authentication) {
        String username = authentication.getName();
        return service.create(request, username);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dockerfile 단건 조회", description = "ID로 Dockerfile을 조회한다.")
    public DockerfileResponse getById(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping
    @Operation(summary = "Dockerfile 목록 조회", description = "프로젝트별 또는 프로젝트+사용자별 Dockerfile 목록을 조회한다.")
    public List<DockerfileResponse> list(
            @Parameter(description = "프로젝트 이름", required = true) @RequestParam String project,
            @Parameter(description = "사용자 이름 (선택)") @RequestParam(required = false) String username) {
        if (username != null) {
            return service.listByProjectAndUser(project, username);
        }
        return service.listByProject(project);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Dockerfile 수정", description = "Dockerfile을 수정하고 새 리비전을 생성한다.")
    public DockerfileResponse update(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id,
            @Valid @RequestBody DockerfileUpdateRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        return service.update(id, request, username);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dockerfile 삭제", description = "ID로 Dockerfile을 삭제한다. 모든 리비전도 함께 삭제된다.")
    public void delete(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id) {
        service.delete(id);
    }

    /* ── Revision endpoints ── */

    @GetMapping("/{id}/revisions")
    @Operation(summary = "리비전 목록 조회", description = "Dockerfile의 모든 리비전을 최신순으로 조회한다.")
    public List<DockerfileRevisionResponse> listRevisions(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id) {
        return revisionService.listRevisions(id);
    }

    @GetMapping("/{id}/revisions/{version}")
    @Operation(summary = "리비전 단건 조회", description = "특정 버전의 리비전을 조회한다.")
    public DockerfileRevisionResponse getRevision(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id,
            @Parameter(description = "버전 번호") @PathVariable int version) {
        return revisionService.getRevision(id, version);
    }

    @PostMapping("/{id}/revisions/{version}/rollback")
    @Operation(summary = "리비전 롤백", description = "지정한 버전의 내용으로 새 리비전을 생성하여 복원한다.")
    public DockerfileResponse rollback(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id,
            @Parameter(description = "복원할 버전 번호") @PathVariable int version,
            Authentication authentication) {
        String username = authentication.getName();
        return revisionService.rollback(id, version, username);
    }

}
