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
            // busybox ls: -l for details, -a for hidden files, -n for numeric uid/gid
            String[] command = {"ls", "-lan", fullPath};

            Process process = exec.exec(namespace, podName, command, podName, false, false);
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("exec ls failed: exitCode={}", exitCode);
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
                .filter(line -> !line.startsWith("total"))  // skip "total N" line from ls
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .filter(e -> !".".equals(e.getName()) && !"..".equals(e.getName()))
                .sorted(Comparator
                        .comparing((FileEntry e) -> e.getType() == FileEntry.FileType.FILE ? 1 : 0)
                        .thenComparing(FileEntry::getName))
                .toList();
    }

    /**
     * Parses a line from `ls -lan` output.
     * Format: "drwxr-xr-x  2  0  0  4096 Jan  1 00:00 dirname"
     * busybox ls -lan may vary slightly but generally:
     * permissions links uid gid size month day time name
     */
    private FileEntry parseLine(String line) {
        // ls -lan output: permissions links uid gid size month day time/year name
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
