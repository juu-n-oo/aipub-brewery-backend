package io.ten1010.aipubbrewerybackend.volume.client;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.ten1010.aipubbrewerybackend.common.exception.ResourceNotFoundException;
import io.ten1010.aipubbrewerybackend.volume.dto.VolumeInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class K8sAipubVolumeClient implements AipubVolumeClient {

    private static final String GROUP = "aipub.ten1010.io";
    private static final String VERSION = "v1alpha1";
    private static final String PLURAL = "aipubvolumes";

    private final CustomObjectsApi customObjectsApi;

    @Override
    @SuppressWarnings("unchecked")
    public List<VolumeInfo> listVolumes(String namespace) {
        try {
            Object result = customObjectsApi.listNamespacedCustomObject(
                    GROUP, VERSION, namespace, PLURAL).execute();

            Map<String, Object> resultMap = (Map<String, Object>) result;
            List<Map<String, Object>> items = (List<Map<String, Object>>) resultMap.get("items");

            return items.stream()
                    .map(K8sAipubVolumeClient::toVolumeInfo)
                    .toList();
        } catch (ApiException e) {
            log.error("Failed to list AIPubVolumes in namespace {}: code={}", namespace, e.getCode(), e);
            throw new RuntimeException("Failed to list volumes: " + e.getResponseBody(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public VolumeInfo getVolume(String namespace, String volumeName) {
        try {
            Object result = customObjectsApi.getNamespacedCustomObject(
                    GROUP, VERSION, namespace, PLURAL, volumeName).execute();
            return toVolumeInfo((Map<String, Object>) result);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new ResourceNotFoundException("AIPubVolume not found: " + namespace + "/" + volumeName);
            }
            throw new RuntimeException("Failed to get AIPubVolume: " + e.getResponseBody(), e);
        }
    }

    @SuppressWarnings("unchecked")
    static VolumeInfo toVolumeInfo(Map<String, Object> crMap) {
        Map<String, Object> metadata = (Map<String, Object>) crMap.get("metadata");
        Map<String, Object> spec = (Map<String, Object>) crMap.getOrDefault("spec", Map.of());
        Map<String, Object> status = (Map<String, Object>) crMap.getOrDefault("status", Map.of());
        Map<String, Object> readyCondition = (Map<String, Object>) status.getOrDefault("readyCondition", Map.of());

        return VolumeInfo.builder()
                .name((String) metadata.get("name"))
                .pvcName((String) status.get("pvcName"))
                .capacity((String) spec.get("capacity"))
                .used((String) status.get("used"))
                .ready(Boolean.TRUE.equals(readyCondition.get("status")))
                .build();
    }

}
