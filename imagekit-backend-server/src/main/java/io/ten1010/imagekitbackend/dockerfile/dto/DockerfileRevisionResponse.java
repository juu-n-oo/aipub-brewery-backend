package io.ten1010.imagekitbackend.dockerfile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Dockerfile 리비전 응답")
public class DockerfileRevisionResponse {

    @Schema(description = "리비전 ID", example = "42")
    private Long id;

    @Schema(description = "Dockerfile ID", example = "1")
    private Long dockerfileId;

    @Schema(description = "버전 번호", example = "3")
    private int version;

    @Schema(description = "Dockerfile 내용")
    private String content;

    @Schema(description = "Base 이미지", example = "pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime")
    private String baseImage;

    @Schema(description = "리비전 메시지", example = "CUDA 버전 업그레이드")
    private String message;

    @Schema(description = "작성자", example = "joonwoo")
    private String createdBy;

    @Schema(description = "생성 시각")
    private Instant createdAt;

}
