package io.ten1010.aipubbrewerybackend.volume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "AIPubVolume 정보")
public class VolumeInfo {

    @Schema(description = "AIPubVolume CR 이름", example = "data-storage")
    private String name;

    @Schema(description = "resolve된 PVC 이름", example = "data-storage-43d77785")
    private String pvcName;

    @Schema(description = "볼륨 용량", example = "150Gi")
    private String capacity;

    @Schema(description = "현재 사용량", example = "0.00Gi")
    private String used;

    @Schema(description = "사용 준비 여부", example = "true")
    private boolean ready;

}
