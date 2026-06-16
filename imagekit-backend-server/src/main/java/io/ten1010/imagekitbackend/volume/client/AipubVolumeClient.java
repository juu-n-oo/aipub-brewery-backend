package io.ten1010.imagekitbackend.volume.client;

import io.ten1010.imagekitbackend.volume.dto.VolumeInfo;

import java.util.List;

public interface AipubVolumeClient {

    List<VolumeInfo> listVolumes(String namespace);

    VolumeInfo getVolume(String namespace, String volumeName);

}
