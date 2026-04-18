package io.ten1010.aipubbrewerybackend.volume.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.aipubbrewerybackend.volume.dto.BrowseResponse;
import io.ten1010.aipubbrewerybackend.volume.dto.VolumeListResponse;
import io.ten1010.aipubbrewerybackend.volume.service.VolumeBrowserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/volumes")
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

}
