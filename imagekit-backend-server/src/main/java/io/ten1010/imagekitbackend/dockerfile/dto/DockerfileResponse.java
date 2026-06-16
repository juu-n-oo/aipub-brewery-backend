package io.ten1010.imagekitbackend.dockerfile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Dockerfile 응답")
public class DockerfileResponse {

    @Schema(description = "Dockerfile ID", example = "1")
    private Long id;

    @Schema(description = "프로젝트 이름", example = "pjw")
    private String project;

    @Schema(description = "소유자 사용자 이름", example = "joonwoo")
    private String username;

    @Schema(description = "Dockerfile 이름", example = "pytorch-cuda12")
    private String name;

    @Schema(description = "설명", example = "PyTorch 2.1 + CUDA 12.1 기반 학습 환경")
    private String description;

    @Schema(description = "Dockerfile 내용", example = "FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nRUN pip install transformers")
    private String content;

    @Schema(description = "Base 이미지 (FROM 대상)", example = "aipub-harbor.cluster7.idc1.ten1010.io/aipub/python:3.11")
    private String baseImage;

    @Schema(description = "생성 시각", example = "2026-04-18T00:00:00Z")
    private Instant createdAt;

    @Schema(description = "수정 시각", example = "2026-04-18T00:00:00Z")
    private Instant updatedAt;

    @Schema(description = "최신 리비전 버전 번호", example = "3")
    private Integer latestVersion;

    @Schema(description = "최신 리비전 ID", example = "42")
    private Long latestRevisionId;

}
