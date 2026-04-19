package io.ten1010.aipubbrewerybackend.volume.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.WebSocketStreamHandler;
import io.kubernetes.client.util.WebSockets;
import io.ten1010.aipubbrewerybackend.common.exception.ResourceNotFoundException;
import io.ten1010.aipubbrewerybackend.volume.client.AipubVolumeClient;
import io.ten1010.aipubbrewerybackend.volume.dto.*;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class VolumeBrowserService {

    private static final String ROOT_PATH = "/";

    private final AipubVolumeClient volumeClient;
    private final ApiClient apiClient;

    public VolumeListResponse listVolumes(String namespace) {
        List<VolumeInfo> volumes = volumeClient.listVolumes(namespace);
        return VolumeListResponse.builder().items(volumes).build();
    }

    public BrowseResponse browse(String namespace, String volumeName, String path) {
        validatePath(path);

        VolumeInfo volumeInfo = volumeClient.getVolume(namespace, volumeName);
        String podName = volumeInfo.getPvcName();

        String fullPath = normalizePath(path);
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

            return parseEntries(output);
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

    private List<FileEntry> parseEntries(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        return output.lines()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("total"))
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .filter(e -> !".".equals(e.getName()) && !"..".equals(e.getName()))
                .sorted(Comparator
                        .comparing((FileEntry e) -> e.getType() == FileEntry.FileType.FILE ? 1 : 0)
                        .thenComparing(FileEntry::getName))
                .toList();
    }

    private FileEntry parseLine(String line) {
        String[] parts = line.trim().split("\\s+", 9);
        if (parts.length < 9) {
            return null;
        }

        String permissions = parts[0];
        FileEntry.FileType type = permissions.startsWith("d") ? FileEntry.FileType.DIRECTORY : FileEntry.FileType.FILE;
        Long size = type == FileEntry.FileType.FILE ? parseLong(parts[4]) : null;
        String modifiedAt = parts[5] + " " + parts[6] + " " + parts[7];
        String name = parts[8];

        return FileEntry.builder()
                .name(name)
                .type(type)
                .size(size)
                .modifiedAt(modifiedAt)
                .build();
    }

    private void validatePath(String path) {
        if (path != null && path.contains("..")) {
            throw new IllegalArgumentException("Path traversal is not allowed: " + path);
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
