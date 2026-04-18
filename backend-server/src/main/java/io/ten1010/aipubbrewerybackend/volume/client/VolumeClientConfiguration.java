package io.ten1010.aipubbrewerybackend.volume.client;

import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@Slf4j
public class VolumeClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "brewery.volume.client-mode", havingValue = "K8S", matchIfMissing = true)
    public AipubVolumeClient k8sAipubVolumeClient(CustomObjectsApi customObjectsApi) {
        log.info("Using K8s client for AIPubVolume operations");
        return new K8sAipubVolumeClient(customObjectsApi);
    }

    @Bean
    @ConditionalOnProperty(name = "brewery.volume.client-mode", havingValue = "PROXY")
    public AipubVolumeClient proxyAipubVolumeClient(VolumeProperties volumeProperties) {
        log.info("Using AIPub proxy for AIPubVolume operations: {}", volumeProperties.getProxyBaseUrl());
        return new ProxyAipubVolumeClient(volumeProperties.getProxyBaseUrl(), RestClient.builder());
    }

}
