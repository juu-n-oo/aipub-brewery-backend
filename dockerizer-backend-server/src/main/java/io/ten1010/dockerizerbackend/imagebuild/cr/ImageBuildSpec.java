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

}
