package io.ten1010.aipubbrewerybackend.volume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "파일/디렉토리 항목")
public class FileEntry {

    @Schema(description = "파일/디렉토리 이름", example = "weights.pt")
    private String name;

    @Schema(description = "유형 (FILE / DIRECTORY)", example = "FILE")
    private FileType type;

    @Schema(description = "파일 크기 (bytes, 디렉토리는 null)", example = "1073741824")
    private Long size;

    @Schema(description = "최종 수정 시각", example = "2026-04-17T12:00:00Z")
    private String modifiedAt;

    public enum FileType {
        FILE, DIRECTORY
    }

}
