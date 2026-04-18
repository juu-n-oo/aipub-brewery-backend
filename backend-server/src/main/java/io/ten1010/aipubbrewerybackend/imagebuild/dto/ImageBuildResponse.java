package io.ten1010.aipubbrewerybackend.imagebuild.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "이미지 빌드 상태 응답")
public class ImageBuildResponse {

    @Schema(description = "ImageBuild CR 이름", example = "imagebuild-a1b2c3d4")
    private String name;

    @Schema(description = "빌드가 실행된 namespace (= project)", example = "pjw")
    private String namespace;

    @Schema(description = "빌드 phase", example = "Building", allowableValues = {"Pending", "Preparing", "Building", "Succeeded", "Failed"})
    private String phase;

    @Schema(description = "대상 이미지 (tag 포함)", example = "harbor.aipub.io/pjw/my-pytorch:v1.0")
    private String targetImage;

    @Schema(description = "상태 메시지", example = "Kaniko job created, building image")
    private String message;

    @Schema(description = "빌드 성공 시 이미지 digest", example = "sha256:abc123...")
    private String imageDigest;

    @Schema(description = "빌드에 사용된 Dockerfile ID", example = "1")
    private Long dockerfileId;

    @Schema(description = "빌드를 실행한 사용자", example = "joonwoo")
    private String username;

    @Schema(description = "빌드 요청 시각 (CR 생성 시각)", example = "2026-04-18T00:00:00Z")
    private Instant createdAt;

    @Schema(description = "빌드 시작 시각", example = "2026-04-18T00:00:00Z")
    private Instant startTime;

    @Schema(description = "빌드 완료 시각", example = "2026-04-18T00:05:00Z")
    private Instant completionTime;

}
