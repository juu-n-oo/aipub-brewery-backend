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

}
