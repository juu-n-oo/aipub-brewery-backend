package io.ten1010.imagekitbackend.volume.client;

import io.ten1010.imagekitbackend.volume.dto.VolumeInfo;

import java.util.List;

/**
 * AIPubVolume CR(목록/단건)을 "어디서 읽을지"를 추상화한다. 반환 타입({@link VolumeInfo})은 동일하며,
 * 데이터 출처만 구현체별로 다르다.
 * <ul>
 *   <li>{@link K8sAipubVolumeClient} — k8s API 직접 호출(공식 client). {@code imagekit.volume.client-mode=K8S}(기본).</li>
 *   <li>{@link ProxyAipubVolumeClient} — AIPub k8sproxy HTTP 경유. {@code imagekit.volume.client-mode=PROXY}.</li>
 * </ul>
 * 둘 중 하나만 {@link VolumeClientConfiguration} 가 프로퍼티로 골라 빈으로 등록하며,
 * 이 빈을 {@code VolumeBrowser}/{@code VolumeUploader} 가 주입받아 Pod 이름 등 메타데이터 조회에 사용한다.
 */
public interface AipubVolumeClient {

    List<VolumeInfo> listVolumes(String namespace);

    VolumeInfo getVolume(String namespace, String volumeName);

}
