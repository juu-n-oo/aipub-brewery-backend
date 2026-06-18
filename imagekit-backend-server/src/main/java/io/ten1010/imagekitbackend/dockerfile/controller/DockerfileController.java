package io.ten1010.imagekitbackend.dockerfile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileRevisionResponse;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.imagekitbackend.aipub.config.AipubProperties;
import io.ten1010.imagekitbackend.dockerfile.service.DockerfileRevisionService;
import io.ten1010.imagekitbackend.dockerfile.service.DockerfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1alpha1/dockerfiles")
@RequiredArgsConstructor
@Tag(name = "Dockerfile", description = "Dockerfile 관리 (CRUD) 및 리비전 이력")
public class DockerfileController {

    private final DockerfileService dockerfileService;
    private final DockerfileRevisionService revisionService;
    private final AipubProperties aipubProperties;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dockerfile 생성", description = "새 Dockerfile을 생성한다. COPY는 허용되며, ADD 지시자가 포함된 경우 reject된다.")
    public DockerfileResponse create(@Valid @RequestBody DockerfileCreateRequest request,
                                     Authentication authentication) {
        String username = authentication.getName();
        return dockerfileService.create(request, username);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dockerfile 단건 조회", description = "ID로 Dockerfile을 조회한다.")
    public DockerfileResponse getById(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id) {
        return dockerfileService.getById(id);
    }

    @GetMapping
    @Operation(summary = "Dockerfile 목록 조회",
            description = "관리자는 전체 Dockerfile 을 생성 일시 최신순으로 조회하며 username 으로 추가 필터링할 수 있다. "
                    + "멤버는 바인딩된 프로젝트(projects) 안의 본인 소유 Dockerfile 만 조회한다. "
                    + "관리자 여부는 백엔드가 토큰 roles 로 판별하므로 별도의 플래그를 신뢰하지 않는다.")
    public List<DockerfileResponse> list(
            @Parameter(description = "바인딩된 프로젝트 목록(멤버 조회). 호출자 본인 소유로 자동 제한된다.")
            @RequestParam(required = false) List<String> projects,
            @Parameter(description = "username 필터(관리자 전용)")
            @RequestParam(required = false) String username,
            Authentication authentication) {
        // 관리자 여부는 토큰 roles(=SecurityContext 권한)로 백엔드가 판별한다(프론트 분기 신뢰 금지).
        // 인증 필터가 selfsubjectreviews 의 roles 를 SecurityContext 권한으로 넣어두므로 그 권한과 대조한다.
        if (isAdmin(authentication)) {
            // 관리자: 전체 조회가 기본이며, username 파라미터가 있으면 해당 사용자 소유만 필터링한다.
            if (username != null && !username.isBlank()) {
                return dockerfileService.listAll(username);
            }
            return dockerfileService.listAll();
        }

        // 멤버: 바인딩된 프로젝트 IN + 토큰의 호출자 본인 이름으로 묶어 본인 Dockerfile 만 조회한다.
        // 임의의 projects 를 넘기더라도 username gate 때문에 본인 Dockerfile 만 반환되므로 안전하다.
        String caller = authentication.getName();
        return dockerfileService.listForUser(projects, caller);
    }

    /** 토큰 roles(=SecurityContext 권한)에 설정된 관리자 role 이 하나라도 포함되면 관리자로 본다. */
    private boolean isAdmin(Authentication authentication) {
        List<String> adminRoles = aipubProperties.getAdminRoles();
        if (adminRoles == null || adminRoles.isEmpty()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(adminRoles::contains);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Dockerfile 수정", description = "Dockerfile을 수정하고 새 리비전을 생성한다.")
    public DockerfileResponse update(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id,
            @Valid @RequestBody DockerfileUpdateRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        return dockerfileService.update(id, request, username);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dockerfile 삭제", description = "ID로 Dockerfile을 삭제한다. 모든 리비전도 함께 삭제된다.")
    public void delete(
            @Parameter(description = "Dockerfile ID") @PathVariable Long id) {
        dockerfileService.delete(id);
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
