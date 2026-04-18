package io.ten1010.aipubbrewerybackend.imagebuild.cr;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildCr {

    private String apiVersion;
    private String kind;
    private Map<String, Object> metadata;
    private ImageBuildSpec spec;
    private ImageBuildStatus status;

}
