package io.ten1010.aipubbrewerybackend.imagebuild.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "이미지 빌드 트리거 요청")
public class ImageBuildRequest {

    @NotNull
    @Schema(description = "빌드할 Dockerfile의 ID", example = "1")
    private Long dockerfileId;

    @NotBlank
    @Schema(description = "대상 이미지 이름 (registry/project/image)", example = "harbor.aipub.io/pjw/my-pytorch")
    private String targetImage;

    @NotBlank
    @Schema(description = "이미지 태그", example = "v1.0")
    private String tag;

    @Schema(description = "이미지 push용 Secret 이름 (미지정 시 기본 패턴 사용)", example = "image-registry-secret-project-aipub-ten1010-io-pjw")
    private String pushSecretRef;

    @Schema(description = "빌드 컨텍스트로 사용할 PVC 이름 (COPY 명령어 사용 시 필요)", example = "data-storage-43d77785")
    private String buildContextPvc;

    @Schema(description = "PVC 내 빌드 컨텍스트 서브 경로 (미지정 시 PVC 루트 사용)", example = "/my-project")
    private String buildContextSubPath;

}
