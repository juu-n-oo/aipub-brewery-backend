package io.ten1010.aipubbrewerybackend.volume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Volume 디렉토리 조회 응답")
public class BrowseResponse {

    @Schema(description = "AIPubVolume 이름", example = "data-storage")
    private String volumeName;

    @Schema(description = "namespace", example = "pjw")
    private String namespace;

    @Schema(description = "조회한 경로", example = "/models")
    private String path;

    @Schema(description = "파일/디렉토리 목록")
    private List<FileEntry> entries;

}
