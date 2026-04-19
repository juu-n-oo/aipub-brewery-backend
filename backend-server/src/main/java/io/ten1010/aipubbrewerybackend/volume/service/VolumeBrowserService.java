package io.ten1010.aipubbrewerybackend.volume.service;

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipubbrewerybackend.common.exception.ResourceNotFoundException;
import io.ten1010.aipubbrewerybackend.volume.client.AipubVolumeClient;
import io.ten1010.aipubbrewerybackend.volume.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VolumeBrowserService {

    private static final String VOLUME_MOUNT_PATH = "/data";

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

        String fullPath = VOLUME_MOUNT_PATH + normalizePath(path);
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
            Exec exec = new Exec(apiClient);
            String[] command = {"ls", "-lan", fullPath};

            Process process = exec.exec(namespace, podName, command, podName, false, false);

            // Wait for the WebSocket connection to be established before reading streams.
            // Exec.exec() starts the WebSocket asynchronously; reading immediately causes
            // IllegalStateException if the connection hasn't opened yet.
            // Give it up to 10 seconds to connect and complete.
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            if (!finished) {
                process.destroyForcibly();
                log.error("exec ls timed out for pod {}/{}", namespace, podName);
                throw new RuntimeException("Exec timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorOutput;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    errorOutput = reader.lines().collect(Collectors.joining("\n"));
                }
                log.warn("exec ls failed: exitCode={}, stdout=[{}], stderr=[{}]", exitCode, output, errorOutput);
                throw new ResourceNotFoundException("Path not found: " + fullPath.replace(VOLUME_MOUNT_PATH, ""));
            }
            log.debug("exec ls output for {}: [{}]", fullPath, output);

            return parseEntries(output);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to exec in pod {}/{}: {}", namespace, podName, e.getMessage(), e);
            throw new RuntimeException("Failed to browse volume files", e);
        }
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
            return "";
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
