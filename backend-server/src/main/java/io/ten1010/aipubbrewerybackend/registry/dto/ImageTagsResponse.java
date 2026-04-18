package io.ten1010.aipubbrewerybackend.registry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "이미지 태그 목록 응답")
public class ImageTagsResponse {

    @Schema(description = "이미지 전체 경로", example = "nvcr.io/nvidia/pytorch")
    private String image;

    @Schema(description = "태그 목록", example = "[\"24.12-py3\", \"24.11-py3\", \"24.10-py3\"]")
    private List<String> tags;

}
