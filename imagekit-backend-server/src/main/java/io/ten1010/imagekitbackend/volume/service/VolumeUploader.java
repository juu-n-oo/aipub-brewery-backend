package io.ten1010.imagekitbackend.volume.service;

import io.ten1010.imagekitbackend.volume.dto.BrowseResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * AIPubVolume PVC 로의 파일 업로드 책임.
 * <p>
 * 현재는 helper Pod 에 {@code dd} 를 exec 하여 multipart 스트림을 패스스루 기록하는
 * {@link PodExecVolumeUploader} 단일 구현만 두지만, 향후 구현 방식을 바꾸기 쉽도록 인터페이스로 분리한다.
 */
public interface VolumeUploader {

    /**
     * 업로드한 파일을 PVC 의 지정 경로에 기록하고, 업로드 직후 대상 디렉토리의 갱신된 목록을 반환한다.
     */
    BrowseResponse upload(String namespace, String volumeName, String path, MultipartFile file);

}
