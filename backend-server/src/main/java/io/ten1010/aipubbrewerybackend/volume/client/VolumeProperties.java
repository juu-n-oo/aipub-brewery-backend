package io.ten1010.aipubbrewerybackend.volume.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "brewery.volume")
@Getter
@Setter
public class VolumeProperties {

    /**
     * Volume 클라이언트 모드.
     * K8S: Kubernetes API 직접 사용 (기본값)
     * PROXY: AIPub k8s proxy 경유
     */
    private String clientMode = "K8S";

    /**
     * PROXY 모드일 때 AIPub 서버 base URL.
     */
    private String proxyBaseUrl = "https://aipub.cluster7.idc1.ten1010.io";

}
