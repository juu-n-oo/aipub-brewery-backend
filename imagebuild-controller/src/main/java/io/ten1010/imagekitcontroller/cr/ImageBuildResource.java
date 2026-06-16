package io.ten1010.imagekitcontroller.cr;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildResource implements KubernetesObject {

    private String apiVersion;
    private String kind;
    private V1ObjectMeta metadata;
    private ImageBuildSpec spec;
    private ImageBuildStatus status;

    public String getName() {
        return metadata != null ? metadata.getName() : null;
    }

    public String getNamespace() {
        return metadata != null ? metadata.getNamespace() : null;
    }

    public String getUid() {
        return metadata != null ? metadata.getUid() : null;
    }

}
