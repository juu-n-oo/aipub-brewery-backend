package io.ten1010.imagekitbackend.volume.client;

import io.ten1010.imagekitbackend.common.exception.ResourceNotFoundException;
import io.ten1010.imagekitbackend.volume.dto.VolumeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * {@link AipubVolumeClient} 의 <b>AIPub k8sproxy 경유</b> 구현. k8s 를 직접 호출하지 않고 AIPub 서버의
 * k8sproxy 엔드포인트({@code .../k8sproxy/apis/aipub.ten1010.io/.../aipubvolumes})로 HTTP({@link RestClient})
 * 요청하며, 권한 중계는 AIPub 프록시가 담당한다. 백엔드가 k8s 에 직접 접근할 수 없는 환경에서 쓴다.
 * <p>
 * {@code imagekit.volume.client-mode=PROXY}일 때 {@link VolumeClientConfiguration} 가 선택하며
 * {@code proxyBaseUrl} 을 base URL 로 사용한다. CR → {@link VolumeInfo} 매핑은
 * {@link K8sAipubVolumeClient#toVolumeInfo} 를 그대로 재사용해 파싱 로직을 일원화한다.
 */
@Slf4j
public class ProxyAipubVolumeClient implements AipubVolumeClient {

    private static final String AIPUB_VOLUME_PATH =
            "/api/v1alpha1/k8sproxy/apis/aipub.ten1010.io/v1alpha1/namespaces/{namespace}/aipubvolumes";
    private static final String AIPUB_VOLUME_SINGLE_PATH =
            "/api/v1alpha1/k8sproxy/apis/aipub.ten1010.io/v1alpha1/namespaces/{namespace}/aipubvolumes/{name}";

    private final RestClient restClient;

    public ProxyAipubVolumeClient(String aipubBaseUrl, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(aipubBaseUrl)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<VolumeInfo> listVolumes(String namespace) {
        Map<String, Object> result = restClient.get()
                .uri(AIPUB_VOLUME_PATH, namespace)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Proxy list volumes failed: namespace={}, status={}", namespace, res.getStatusCode());
                    throw new RuntimeException("Failed to list volumes via proxy: " + res.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {});

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        return items.stream()
                .map(K8sAipubVolumeClient::toVolumeInfo)
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public VolumeInfo getVolume(String namespace, String volumeName) {
        Map<String, Object> result = restClient.get()
                .uri(AIPUB_VOLUME_SINGLE_PATH, namespace, volumeName)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new ResourceNotFoundException("AIPubVolume not found: " + namespace + "/" + volumeName);
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Proxy get volume failed: {}/{}, status={}", namespace, volumeName, res.getStatusCode());
                    throw new RuntimeException("Failed to get volume via proxy: " + res.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {});

        return K8sAipubVolumeClient.toVolumeInfo(result);
    }

}
