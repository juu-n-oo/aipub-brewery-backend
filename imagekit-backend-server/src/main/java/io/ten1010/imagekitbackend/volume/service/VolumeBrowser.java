package io.ten1010.imagekitbackend.volume.service;

import io.ten1010.imagekitbackend.volume.dto.BrowseResponse;
import io.ten1010.imagekitbackend.volume.dto.VolumeListResponse;

/**
 * AIPubVolume 목록 조회 및 PVC 내 파일/디렉토리 브라우징(조회) 책임.
 * <p>
 * 현재는 helper Pod 에 {@code ls} 를 exec 하는 {@link PodExecVolumeBrowser} 단일 구현만 두지만,
 * 향후 구현 방식(AIPub proxy, SFTP 등)을 바꾸기 쉽도록 인터페이스로 분리한다.
 */
public interface VolumeBrowser {

    VolumeListResponse listVolumes(String namespace);

    BrowseResponse browse(String namespace, String volumeName, String path);

}
