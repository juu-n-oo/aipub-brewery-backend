package io.ten1010.aipubbrewerybackend.dockerfile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.BuildContextFileResponse;
import io.ten1010.aipubbrewerybackend.dockerfile.service.BuildContextFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dockerfiles/{dockerfileId}/files")
@RequiredArgsConstructor
@Tag(name = "BuildContextFile", description = "빌드 컨텍스트 파일 관리 (업로드/조회/삭제)")
public class BuildContextFileController {

    private final BuildContextFileService service;

    @GetMapping
    @Operation(summary = "빌드 컨텍스트 파일 목록 조회", description = "Dockerfile에 연결된 빌드 컨텍스트 파일 목록을 조회한다.")
    public List<BuildContextFileResponse> listFiles(
            @Parameter(description = "Dockerfile ID") @PathVariable Long dockerfileId) {
        return service.listFiles(dockerfileId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "빌드 컨텍스트 파일 업로드", description = "Dockerfile의 빌드 컨텍스트에 파일을 업로드한다. COPY 명령에서 참조할 수 있다.")
    public BuildContextFileResponse uploadFile(
            @Parameter(description = "Dockerfile ID") @PathVariable Long dockerfileId,
            @Parameter(description = "업로드할 파일") @RequestPart("file") MultipartFile file,
            @Parameter(description = "빌드 컨텍스트 내 경로", example = "requirements.txt") @RequestParam String targetPath) {
        return service.uploadFile(dockerfileId, file, targetPath);
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "빌드 컨텍스트 파일 삭제", description = "업로드된 빌드 컨텍스트 파일을 삭제한다.")
    public void deleteFile(
            @Parameter(description = "Dockerfile ID") @PathVariable Long dockerfileId,
            @Parameter(description = "파일 ID") @PathVariable Long fileId) {
        service.deleteFile(dockerfileId, fileId);
    }

}
