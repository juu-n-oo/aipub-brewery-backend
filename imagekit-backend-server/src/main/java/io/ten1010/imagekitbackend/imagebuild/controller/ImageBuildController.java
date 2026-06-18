package io.ten1010.imagekitbackend.imagebuild.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.imagekitbackend.imagebuild.service.ImageBuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 빌드 트리거·목록·상태 조회는 프론트가 k8sproxy 로 ImageBuild CR 을 직접 생성/조회하므로 REST 엔드포인트를 두지 않는다.
// 이 API 는 k8sproxy 로 대체할 수 없는 로그 조회(Pod 로그 일회성 · SSE 스트리밍)만 제공한다.
@RestController
@RequestMapping("/api/v1alpha1/builds")
@RequiredArgsConstructor
@Tag(name = "ImageBuild", description = "이미지 빌드 로그 조회 (일회성 · SSE 스트리밍)")
public class ImageBuildController {

    private final ImageBuildService service;

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
