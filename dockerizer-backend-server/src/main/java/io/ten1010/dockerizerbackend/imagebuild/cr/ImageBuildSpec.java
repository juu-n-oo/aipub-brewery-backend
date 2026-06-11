package io.ten1010.dockerizerbackend.imagebuild.cr;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildSpec {

    private String dockerfileContent;
    private String targetImage;
    private String pushSecretRef;
    private String buildContextPvc;
    private String buildContextSubPath;
    /** 빌드 Job 의 최대 wall-clock 시간(초). 미지정 시 컨트롤러 기본값(3600s) 적용. */
    private Integer buildTimeoutSeconds;

}
