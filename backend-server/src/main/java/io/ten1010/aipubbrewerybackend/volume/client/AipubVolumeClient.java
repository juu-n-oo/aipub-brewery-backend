package io.ten1010.aipubbrewerybackend.volume.client;

import io.ten1010.aipubbrewerybackend.volume.dto.VolumeInfo;

import java.util.List;

public interface AipubVolumeClient {

    List<VolumeInfo> listVolumes(String namespace);

    VolumeInfo getVolume(String namespace, String volumeName);

}
