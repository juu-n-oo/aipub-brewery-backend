package io.ten1010.imagekitbackend.volume.client;

import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * {@code imagekit.volume.client-mode} 프로퍼티에 따라 {@link AipubVolumeClient} 구현을 <b>하나만</b> 빈으로 등록한다.
 * <ul>
 *   <li>{@code K8S}(기본, 미지정 시 포함) → {@link K8sAipubVolumeClient} (k8s API 직접)</li>
 *   <li>{@code PROXY} → {@link ProxyAipubVolumeClient} (AIPub k8sproxy 경유)</li>
 * </ul>
 */
@Configuration
@Slf4j
public class VolumeClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "imagekit.volume.client-mode", havingValue = "K8S", matchIfMissing = true)
    public AipubVolumeClient k8sAipubVolumeClient(CustomObjectsApi customObjectsApi) {
        log.info("Using K8s client for AIPubVolume operations");
        return new K8sAipubVolumeClient(customObjectsApi);
    }

    @Bean
    @ConditionalOnProperty(name = "imagekit.volume.client-mode", havingValue = "PROXY")
    public AipubVolumeClient proxyAipubVolumeClient(VolumeProperties volumeProperties) {
        log.info("Using AIPub proxy for AIPubVolume operations: {}", volumeProperties.getProxyBaseUrl());
        return new ProxyAipubVolumeClient(volumeProperties.getProxyBaseUrl(), RestClient.builder());
    }

}
