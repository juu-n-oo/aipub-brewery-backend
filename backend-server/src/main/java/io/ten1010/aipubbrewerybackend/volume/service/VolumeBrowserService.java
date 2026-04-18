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
            String[] command = {
                    "find", fullPath, "-maxdepth", "1", "-not", "-path", fullPath,
                    "-printf", "%f\\t%y\\t%s\\t%T+\\n"
            };

            Process process = exec.exec(namespace, podName, command, podName, false, false);
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorOutput;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    errorOutput = reader.lines().collect(Collectors.joining("\n"));
                }
                log.warn("exec find failed: exitCode={}, stderr={}", exitCode, errorOutput);
                throw new ResourceNotFoundException("Path not found: " + fullPath.replace(VOLUME_MOUNT_PATH, ""));
            }

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
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((FileEntry e) -> e.getType() == FileEntry.FileType.FILE ? 1 : 0)
                        .thenComparing(FileEntry::getName))
                .toList();
    }

    private FileEntry parseLine(String line) {
        String[] parts = line.split("\t", 4);
        if (parts.length < 4) {
            return null;
        }

        String name = parts[0];
        FileEntry.FileType type = "d".equals(parts[1]) ? FileEntry.FileType.DIRECTORY : FileEntry.FileType.FILE;
        Long size = type == FileEntry.FileType.FILE ? parseLong(parts[2]) : null;
        String modifiedAt = parts[3].trim();

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
