package io.ten1010.dockerizercontroller.cr;

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
    private String pushSecretRef;
    private String buildContextPvc;
    private String buildContextSubPath;
    /** OCI/provenance labels to bake into the built image config (applied via Kaniko {@code --label key=value}). */
    private Map<String, String> imageLabels;
    /**
     * 빌드 Job 의 최대 wall-clock 시간(초) — Job {@code activeDeadlineSeconds} 로 적용된다.
     * 미지정(null) 또는 0 이하면 컨트롤러 기본값({@code ControllerProperties.buildTimeoutSeconds})을 쓴다.
     */
    private Integer buildTimeoutSeconds;

}
