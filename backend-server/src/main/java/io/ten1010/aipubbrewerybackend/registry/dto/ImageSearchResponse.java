package io.ten1010.aipubbrewerybackend.registry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "이미지 검색 응답")
public class ImageSearchResponse {

    @Schema(description = "검색 결과 목록")
    private List<RegistryImage> images;

    @Schema(description = "전체 결과 수", example = "42")
    private int totalCount;

}
