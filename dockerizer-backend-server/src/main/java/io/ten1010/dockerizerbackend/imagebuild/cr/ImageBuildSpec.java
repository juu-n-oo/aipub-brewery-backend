package io.ten1010.dockerizerbackend.imagebuild.cr;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildSpec {

    private String dockerfileContent;
    private String targetImage;
    /** 이미지에 baking 할 라벨 맵. 컨트롤러가 Kaniko {@code --label} 로 전개한다(프론트 buildImageLabels 가 채움). */
    private Map<String, String> imageLabels;
    private String pushSecretRef;
    private String buildContextPvc;
    private String buildContextSubPath;
    /** 빌드 Job 의 최대 wall-clock 시간(초). 미지정 시 컨트롤러 기본값(3600s) 적용. */
    private Integer buildTimeoutSeconds;

}
