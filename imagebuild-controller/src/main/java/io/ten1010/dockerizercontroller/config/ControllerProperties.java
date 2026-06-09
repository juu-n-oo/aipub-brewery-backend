package io.ten1010.dockerizercontroller.config;

import io.ten1010.dockerizercontroller.cr.ImageBuildConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dockerizer.imagebuild")
@Getter
@Setter
public class ControllerProperties {

    private String group = ImageBuildConstants.GROUP;
    private String version = ImageBuildConstants.VERSION;
    private String plural = ImageBuildConstants.PLURAL;
    private String kanikoImage = ImageBuildConstants.KANIKO_DEFAULT_IMAGE;
    // 빌드 Job(과 Pod) 이 완료 후 GC 되기까지의 시간(초). 이 시간이 지나면 Pod 로그가
    // 소실되어 OpenSearch fallback 으로만 조회 가능하다.
    private Integer jobTtlSeconds = 3600;

}
