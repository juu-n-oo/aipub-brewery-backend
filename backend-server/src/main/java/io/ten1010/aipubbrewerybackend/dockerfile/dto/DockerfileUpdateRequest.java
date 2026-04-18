package io.ten1010.aipubbrewerybackend.dockerfile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Dockerfile 수정 요청")
public class DockerfileUpdateRequest {

    @Schema(description = "Dockerfile 이름 (선택, 미입력 시 변경 안 함)", example = "pytorch-cuda12-v2")
    private String name;

    @Schema(description = "설명 (선택)", example = "PyTorch 2.1 + CUDA 12.1 기반 학습 환경 (업데이트)")
    private String description;

    @NotBlank
    @Schema(description = "Dockerfile 내용", example = "FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nRUN pip install transformers datasets")
    private String content;

}
