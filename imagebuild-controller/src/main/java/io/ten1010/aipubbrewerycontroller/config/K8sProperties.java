package io.ten1010.aipubbrewerycontroller.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "brewery.kubernetes")
@Getter
@Setter
public class K8sProperties {

    @Nullable
    private Boolean verifySsl;

    @Nullable
    private KubeConfigProperty kubeConfig;

    public enum KubeConfigMode {
        IN_CLUSTER, FILE
    }

    @Getter
    @Setter
    public static class KubeConfigProperty {

        @Nullable
        private KubeConfigMode mode;

        @Nullable
        private String kubeConfigPath;

    }

}
