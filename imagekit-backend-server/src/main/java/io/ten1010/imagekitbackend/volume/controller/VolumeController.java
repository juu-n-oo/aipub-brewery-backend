package io.ten1010.imagekitbackend.volume.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.imagekitbackend.volume.dto.BrowseResponse;
import io.ten1010.imagekitbackend.volume.dto.VolumeListResponse;
import io.ten1010.imagekitbackend.volume.service.VolumeBrowserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1alpha1/volumes")
@RequiredArgsConstructor
@Tag(name = "Volume", description = "AIPubVolume 조회 및 파일 브라우저")
public class VolumeController {

    private final VolumeBrowserService service;

    @GetMapping("/{namespace}")
    @Operation(summary = "Volume 목록 조회", description = "프로젝트(namespace)의 AIPubVolume 목록을 조회한다.")
    public VolumeListResponse listVolumes(
            @Parameter(description = "프로젝트 namespace") @PathVariable String namespace) {
        return service.listVolumes(namespace);
    }

    @GetMapping("/{namespace}/{volumeName}/browse")
    @Operation(summary = "Volume 파일 브라우저", description = "AIPubVolume PVC 내 지정 경로의 파일/디렉토리 목록을 조회한다. ls 명령과 유사.")
    public BrowseResponse browse(
            @Parameter(description = "프로젝트 namespace") @PathVariable String namespace,
            @Parameter(description = "AIPubVolume 이름") @PathVariable String volumeName,
            @Parameter(description = "조회할 경로 (기본: /)", example = "/models") @RequestParam(defaultValue = "/") String path) {
        return service.browse(namespace, volumeName, path);
    }

    @PostMapping(value = "/{namespace}/{volumeName}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Volume 파일 업로드", description = "AIPubVolume PVC 의 지정 경로(path)에 파일을 업로드한다. " +
            "helper Pod 에 exec(dd) 로 multipart 스트림을 그대로 기록하며, 응답으로 해당 경로의 갱신된 목록을 반환한다.")
    public BrowseResponse upload(
            @Parameter(description = "프로젝트 namespace") @PathVariable String namespace,
            @Parameter(description = "AIPubVolume 이름") @PathVariable String volumeName,
            @Parameter(description = "업로드 대상 디렉토리 (기본: /)", example = "/models") @RequestParam(defaultValue = "/") String path,
            @Parameter(description = "업로드할 파일") @RequestPart("file") MultipartFile file) {
        return service.upload(namespace, volumeName, path, file);
    }

}
