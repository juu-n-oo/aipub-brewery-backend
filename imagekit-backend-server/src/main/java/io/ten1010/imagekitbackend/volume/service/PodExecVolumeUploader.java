package io.ten1010.imagekitbackend.volume.service;

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.imagekitbackend.volume.client.AipubVolumeClient;
import io.ten1010.imagekitbackend.volume.client.VolumeProperties;
import io.ten1010.imagekitbackend.volume.dto.BrowseResponse;
import io.ten1010.imagekitbackend.volume.dto.VolumeInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * helper Pod 에 {@code dd} 를 exec 하여 multipart 스트림을 PVC 파일로 패스스루 기록하는
 * {@link VolumeUploader} 구현.
 * <p>
 * k8sproxy 는 WebSocket(exec) 업그레이드를 지원하지 않으므로, 프론트는 imagekit 백엔드의
 * 이 엔드포인트로 multipart 를 보내고, 백엔드가 PVC 를 RW 로 마운트한 helper Pod 에
 * {@code dd of=<경로>} 를 exec 하여 multipart 스트림을 그대로 stdin 으로 흘려보낸다.
 * (로컬 디스크를 거치지 않는 패스스루.) 빌드 시에는 이 볼륨의 PVC 가 그대로 빌드 컨텍스트로
 * 마운트되어 COPY 가 동작한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PodExecVolumeUploader implements VolumeUploader {

    private final AipubVolumeClient volumeClient;
    private final VolumeProperties volumeProperties;
    private final ApiClient apiClient;
    // 업로드 직후 갱신된 디렉토리 목록을 반환하기 위해 조회 책임을 위임한다.
    private final VolumeBrowser volumeBrowser;

    @Override
    public BrowseResponse upload(String namespace, String volumeName, String path, MultipartFile file) {
        VolumePaths.validate(path);
        String filename = VolumePaths.resolveFilename(file.getOriginalFilename());

        VolumeInfo volumeInfo = volumeClient.getVolume(namespace, volumeName);
        String podName = volumeInfo.getPvcName();
        String pvcMountPath = volumeProperties.getPvcMountPath();

        String userPath = VolumePaths.normalize(path);
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
        return volumeBrowser.browse(namespace, volumeName, path);
    }

}
