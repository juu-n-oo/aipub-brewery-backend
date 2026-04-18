package io.ten1010.aipubbrewerycontroller.cr;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildResource {

    private String apiVersion;
    private String kind;
    private Map<String, Object> metadata;
    private ImageBuildSpec spec;
    private ImageBuildStatus status;

    public String getName() {
        return metadata != null ? (String) metadata.get("name") : null;
    }

    public String getNamespace() {
        return metadata != null ? (String) metadata.get("namespace") : null;
    }

    public String getUid() {
        return metadata != null ? (String) metadata.get("uid") : null;
    }

}
