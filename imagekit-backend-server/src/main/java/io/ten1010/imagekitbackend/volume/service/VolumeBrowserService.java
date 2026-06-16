package io.ten1010.imagekitbackend.volume.service;

import io.kubernetes.client.Exec;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VolumeBrowserService {

    private final AipubVolumeClient volumeClient;
    private final VolumeProperties volumeProperties;
    private final ApiClient apiClient;

    public VolumeListResponse listVolumes(String namespace) {
        List<VolumeInfo> volumes = volumeClient.listVolumes(namespace);
        return VolumeListResponse.builder().items(volumes).build();
    }

    public BrowseResponse browse(String namespace, String volumeName, String path) {
        validatePath(path);

        VolumeInfo volumeInfo = volumeClient.getVolume(namespace, volumeName);
        String podName = volumeInfo.getPvcName();
        String pvcMountPath = volumeProperties.getPvcMountPath();

        // User path is relative to PVC root; actual path = mountPath + userPath
        String userPath = normalizePath(path);
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

    /**
     * 업로드한 파일을 AIPubVolume PVC 의 지정 경로에 기록한다.
     * <p>
     * k8sproxy 는 WebSocket(exec) 업그레이드를 지원하지 않으므로, 프론트는 imagekit 백엔드의
     * 이 엔드포인트로 multipart 를 보내고, 백엔드가 PVC 를 RW 로 마운트한 helper Pod 에
     * {@code dd of=<경로>} 를 exec 하여 multipart 스트림을 그대로 stdin 으로 흘려보낸다.
     * (로컬 디스크를 거치지 않는 패스스루.) 빌드 시에는 이 볼륨의 PVC 가 그대로 빌드 컨텍스트로
     * 마운트되어 COPY 가 동작한다.
     */
    public BrowseResponse upload(String namespace, String volumeName, String path, MultipartFile file) {
        validatePath(path);
        String filename = resolveFilename(file.getOriginalFilename());

        VolumeInfo volumeInfo = volumeClient.getVolume(namespace, volumeName);
        String podName = volumeInfo.getPvcName();
        String pvcMountPath = volumeProperties.getPvcMountPath();

        String userPath = normalizePath(path);
        String destDir = "/".equals(userPath) ? pvcMountPath : pvcMountPath + userPath;
        // destDir 가 / 로 끝나면(루트 마운트) 중복 슬래시 방지
        String fullPath = destDir.endsWith("/") ? destDir + filename : destDir + "/" + filename;

        log.info("Uploading file [{}] ({} bytes) to volume {}/{} at {}",
                filename, file.getSize(), namespace, volumeName, fullPath);

        Process proc = null;
        try {
            Exec exec = new Exec(apiClient);
            // 셸을 거치지 않고 인자로 직접 전달 → 경로/파일명 셸 인젝션 불가.
            proc = exec.exec(
                    namespace,
                    podName,
                    new String[]{"dd", "of=" + fullPath, "bs=65536"},
                    podName,
                    true,
                    false);

            // dd 는 전송 리포트를 stderr 로 출력한다. 별도 스레드로 비워 블로킹을 방지한다.
            final Process running = proc;
            Thread errDrain = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(running.getErrorStream(), StandardCharsets.UTF_8))) {
                    String err = r.lines().collect(Collectors.joining("\n"));
                    if (!err.isBlank()) {
                        log.debug("dd stderr for {}: {}", fullPath, err);
                    }
                } catch (Exception ignored) {
                    // 드레인 실패는 업로드 결과에 영향 없음
                }
            }, "volume-upload-stderr");
            errDrain.setDaemon(true);
            errDrain.start();

            // multipart → exec stdin 패스스루
            try (InputStream in = file.getInputStream(); OutputStream out = proc.getOutputStream()) {
                in.transferTo(out);
            }
            // stdout 은 비어있어야 하지만, 혹시 모를 데이터로 인한 파이프 블로킹 방지
            try (InputStream stdout = proc.getInputStream()) {
                stdout.readAllBytes();
            } catch (Exception ignored) {
                // 무시
            }

            int code = proc.waitFor();
            errDrain.join(5000);
            if (code != 0) {
                throw new RuntimeException("File upload failed (dd exit code " + code + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Upload interrupted", e);
        } catch (Exception e) {
            log.error("Failed to upload file to volume {}/{}: {}", namespace, volumeName, e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to volume", e);
        } finally {
            if (proc != null) {
                proc.destroy();
            }
        }

        // 업로드 직후의 디렉토리 목록을 반환하여 프론트가 바로 갱신할 수 있게 한다.
        return browse(namespace, volumeName, path);
    }

    /**
     * 업로드 파일명을 안전한 단일 파일명으로 정규화한다.
     * 경로 구분자나 {@code ..} 를 포함하면 거부하여 디렉토리 이탈을 막는다.
     */
    private String resolveFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("Uploaded file name is required");
        }
        String name = Path.of(original).getFileName().toString();
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name: " + original);
        }
        return name;
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
