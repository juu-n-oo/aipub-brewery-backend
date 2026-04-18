package io.ten1010.aipubbrewerybackend.registry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "컨테이너 이미지 정보")
public class RegistryImage {

    @Schema(description = "이미지 이름", example = "pytorch")
    private String name;

    @Schema(description = "이미지 전체 경로", example = "nvcr.io/nvidia/pytorch")
    private String fullPath;

    @Schema(description = "설명", example = "Optimized PyTorch container for GPU-accelerated deep learning")
    private String description;

    @Schema(description = "레지스트리 소스", example = "NGC", allowableValues = {"NGC", "HUGGINGFACE"})
    private String source;

}
