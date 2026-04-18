package io.ten1010.aipubbrewerybackend.dockerfile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.service.DockerfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dockerfiles")
@RequiredArgsConstructor
@Tag(name = "Dockerfile", description = "Dockerfile 관리 (CRUD)")
public class DockerfileController {

    private final DockerfileService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dockerfile 생성", description = "새 Dockerfile을 생성한다. COPY는 허용되며, ADD 지시자가 포함된 경우 reject된다.")
    public DockerfileResponse create(@Valid @RequestBody DockerfileCreateRequest request) {
        return service.create(request);
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
    @Operation(summary = "Dockerfile 수정", description = "Dockerfile의 name, description, content를 수정한다. ADD 지시자가 포함된 경우 reject된다.")
    public DockerfileResponse update(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id,
            @Valid @RequestBody DockerfileUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dockerfile 삭제", description = "ID로 Dockerfile을 삭제한다.")
    public void delete(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id) {
        service.delete(id);
    }

}
