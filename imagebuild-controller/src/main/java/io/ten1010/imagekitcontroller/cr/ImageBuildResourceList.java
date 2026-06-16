package io.ten1010.imagekitcontroller.cr;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildResourceList implements KubernetesListObject {

    private String apiVersion;
    private String kind;
    private V1ListMeta metadata;
    private List<ImageBuildResource> items;

}
