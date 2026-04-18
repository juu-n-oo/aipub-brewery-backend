package io.ten1010.aipubbrewerybackend.imagebuild.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildRequest;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildResponse;
import io.ten1010.aipubbrewerybackend.imagebuild.service.ImageBuildService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/builds")
@RequiredArgsConstructor
@Tag(name = "ImageBuild", description = "이미지 빌드 관리 (트리거, 목록, 상태 조회, 로그)")
public class ImageBuildController {

    private final ImageBuildService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "이미지 빌드 트리거", description = "저장된 Dockerfile로 ImageBuild CR을 생성하여 Kaniko 빌드를 시작한다.")
    public ImageBuildResponse triggerBuild(@Valid @RequestBody ImageBuildRequest request) {
        return service.triggerBuild(request);
    }

    @GetMapping
    @Operation(summary = "빌드 목록 조회", description = "프로젝트(namespace)의 ImageBuild 목록을 조회한다.")
    public List<ImageBuildResponse> listBuilds(
            @Parameter(description = "프로젝트 namespace", required = true) @RequestParam String project) {
        return service.listBuilds(project);
    }

    @GetMapping("/{namespace}/{name}")
    @Operation(summary = "빌드 상태 조회", description = "ImageBuild CR의 현재 상태를 조회한다.")
    public ImageBuildResponse getBuildStatus(
            @Parameter(description = "빌드가 실행된 namespace (= project)") @PathVariable String namespace,
            @Parameter(description = "ImageBuild CR 이름") @PathVariable String name) {
        return service.getBuildStatus(namespace, name);
    }

    @GetMapping(value = "/{namespace}/{name}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "빌드 로그 조회", description = "Kaniko 빌드 Pod의 로그를 일회성으로 조회한다. 빌드 완료 후 사용.")
    public String getBuildLogs(
            @Parameter(description = "빌드가 실행된 namespace (= project)") @PathVariable String namespace,
            @Parameter(description = "ImageBuild CR 이름") @PathVariable String name) {
        return service.getBuildLogs(namespace, name);
    }

    @GetMapping(value = "/{namespace}/{name}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "빌드 로그 실시간 스트리밍", description = "Kaniko 빌드 Pod의 로그를 SSE로 실시간 스트리밍한다. 빌드 진행 중 사용. 완료 시 'done' 이벤트 발송.")
    public SseEmitter streamBuildLogs(
            @Parameter(description = "빌드가 실행된 namespace (= project)") @PathVariable String namespace,
            @Parameter(description = "ImageBuild CR 이름") @PathVariable String name) {
        return service.streamBuildLogs(namespace, name);
    }

}
