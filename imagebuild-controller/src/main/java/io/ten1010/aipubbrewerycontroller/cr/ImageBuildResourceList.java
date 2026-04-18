package io.ten1010.aipubbrewerycontroller.cr;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildResourceList {

    private String apiVersion;
    private String kind;
    private Map<String, Object> metadata;
    private List<ImageBuildResource> items;

}
