package io.ten1010.aipubbrewerycontroller.config;

import io.ten1010.aipubbrewerycontroller.cr.ImageBuildConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "brewery.imagebuild")
@Getter
@Setter
public class ControllerProperties {

    private String group = ImageBuildConstants.GROUP;
    private String version = ImageBuildConstants.VERSION;
    private String plural = ImageBuildConstants.PLURAL;
    private String kanikoImage = "gcr.io/kaniko-project/executor:latest";

}
