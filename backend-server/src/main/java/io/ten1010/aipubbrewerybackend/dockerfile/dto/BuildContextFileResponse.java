package io.ten1010.aipubbrewerybackend.dockerfile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "빌드 컨텍스트 파일 응답")
public class BuildContextFileResponse {

    @Schema(description = "파일 ID", example = "1")
    private Long id;

    @Schema(description = "원본 파일 이름", example = "requirements.txt")
    private String fileName;

    @Schema(description = "빌드 컨텍스트 내 경로", example = "requirements.txt")
    private String targetPath;

    @Schema(description = "파일 크기 (bytes)", example = "256")
    private Long fileSize;

    @Schema(description = "업로드 시각", example = "2026-04-18T00:00:00Z")
    private Instant uploadedAt;

}
