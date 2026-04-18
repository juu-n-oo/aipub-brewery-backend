package io.ten1010.aipubbrewerybackend.volume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "AIPubVolume 목록 응답")
public class VolumeListResponse {

    @Schema(description = "AIPubVolume 목록")
    private List<VolumeInfo> items;

}
