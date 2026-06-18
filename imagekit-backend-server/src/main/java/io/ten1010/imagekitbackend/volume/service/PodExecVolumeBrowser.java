package io.ten1010.imagekitbackend.volume.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.WebSocketStreamHandler;
import io.kubernetes.client.util.WebSockets;
import io.ten1010.imagekitbackend.common.exception.ResourceNotFoundException;
import io.ten1010.imagekitbackend.volume.client.AipubVolumeClient;
import io.ten1010.imagekitbackend.volume.client.VolumeProperties;
import io.ten1010.imagekitbackend.volume.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * helper Pod 에 {@code ls} 를 exec(WebSocket) 하여 AIPubVolume PVC 의 파일 목록을 조회하는
 * {@link VolumeBrowser} 구현. (k8sproxy 가 exec WebSocket 업그레이드를 지원하지 않아 백엔드를 경유한다.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PodExecVolumeBrowser implements VolumeBrowser {

    private final AipubVolumeClient volumeClient;
    private final VolumeProperties volumeProperties;
    private final ApiClient apiClient;

    @Override
    public VolumeListResponse listVolumes(String namespace) {
        List<VolumeInfo> volumes = volumeClient.listVolumes(namespace);
        return VolumeListResponse.builder().items(volumes).build();
    }

    @Override
    public BrowseResponse browse(String namespace, String volumeName, String path) {
        VolumePaths.validate(path);

        VolumeInfo volumeInfo = volumeClient.getVolume(namespace, volumeName);
        String podName = volumeInfo.getPvcName();
        String pvcMountPath = volumeProperties.getPvcMountPath();

        // User path is relative to PVC root; actual path = mountPath + userPath
        String userPath = VolumePaths.normalize(path);
        String fullPath = "/".equals(userPath)
                ? pvcMountPath
                : pvcMountPath + userPath;
        List<FileEntry> entries = execListFiles(namespace, podName, fullPath);

        return BrowseResponse.builder()
                .volumeName(volumeName)
                .namespace(namespace)
                .path(path)
                .entries(entries)
                .build();
    }

    private List<FileEntry> execListFiles(String namespace, String podName, String fullPath) {
        try {
            String[] command = {"ls", "-lan", fullPath};
            String execPath = buildExecPath(namespace, podName, podName, command);

            WebSocketStreamHandler handler = new WebSocketStreamHandler();

            // Pre-create the stdout/stderr input streams BEFORE the WebSocket connects.
            // This ensures the piped streams are ready to receive data when messages arrive.
            // Without this, data can arrive and be written before getInputStream() creates the pipe,
            // or the WebSocket can close before we call getInputStream(), causing IllegalStateException.
            InputStream stdout = handler.getInputStream(1);
            InputStream stderr = handler.getInputStream(2);

            WebSockets.stream(execPath, "GET", apiClient, handler);

            // Read stdout - this blocks until the WebSocket closes (command finishes)
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            log.debug("exec ls output for {}: [{}]", fullPath, output);

            // Check stderr if stdout was empty
            if (output.isBlank()) {
                String errorOutput;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                    errorOutput = reader.lines().collect(Collectors.joining("\n"));
                }
                if (!errorOutput.isBlank()) {
                    log.warn("exec ls stderr for {}: [{}]", fullPath, errorOutput);
                    throw new ResourceNotFoundException("Path not found: " + fullPath);
                }
            }

            return LsOutputParser.parse(output);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (ApiException e) {
            log.error("Failed to exec in pod {}/{}: code={}, body={}", namespace, podName, e.getCode(), e.getResponseBody(), e);
            throw new RuntimeException("Failed to browse volume files", e);
        } catch (Exception e) {
            log.error("Failed to exec in pod {}/{}: {}", namespace, podName, e.getMessage(), e);
            throw new RuntimeException("Failed to browse volume files", e);
        }
    }

    private String buildExecPath(String namespace, String podName, String container, String[] command) {
        StringBuilder sb = new StringBuilder();
        sb.append("/api/v1/namespaces/")
                .append(namespace)
                .append("/pods/")
                .append(podName)
                .append("/exec?");

        for (String cmd : command) {
            sb.append("command=").append(URLEncoder.encode(cmd, StandardCharsets.UTF_8)).append("&");
        }
        sb.append("container=").append(URLEncoder.encode(container, StandardCharsets.UTF_8));
        sb.append("&stdout=true&stderr=true");

        return sb.toString();
    }

}
