package io.ten1010.aipubbrewerybackend.common.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

@Configuration
public class KubernetesConfiguration {

    @Bean
    public ApiClient apiClient(K8sProperties k8sProperties) throws IOException {
        Objects.requireNonNull(k8sProperties.getKubeConfig(), "brewery.kubernetes.kube-config must be configured");
        Objects.requireNonNull(k8sProperties.getKubeConfig().getMode(), "brewery.kubernetes.kube-config.mode must be configured");
        Objects.requireNonNull(k8sProperties.getVerifySsl(), "brewery.kubernetes.verify-ssl must be configured");

        K8sProperties.KubeConfigProperty kubeConfigProperty = k8sProperties.getKubeConfig();
        return switch (kubeConfigProperty.getMode()) {
            case IN_CLUSTER -> {
                ApiClient client = ClientBuilder
                        .cluster()
                        .setVerifyingSsl(k8sProperties.getVerifySsl())
                        .build();
                // ClientBuilder.cluster() sets auth via interceptor only.
                // WebSocket connections (used by Exec) build requests via ApiClient.buildRequest()
                // which reads from the authentications map, not interceptors.
                // Explicitly set the BearerToken so WebSocket auth works.
                String token = Files.readString(
                        Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token"));
                client.setApiKeyPrefix("Bearer");
                client.setApiKey(token);
                yield client;
            }
            case FILE -> {
                Objects.requireNonNull(kubeConfigProperty.getKubeConfigPath(),
                        "brewery.kubernetes.kube-config.kube-config-path must be configured when mode is FILE");
                FileReader configFileReader = new FileReader(kubeConfigProperty.getKubeConfigPath());
                yield ClientBuilder
                        .kubeconfig(KubeConfig.loadKubeConfig(configFileReader))
                        .setVerifyingSsl(k8sProperties.getVerifySsl())
                        .build();
            }
        };
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Bean
    public CustomObjectsApi customObjectsApi(ApiClient apiClient) {
        return new CustomObjectsApi(apiClient);
    }

}
