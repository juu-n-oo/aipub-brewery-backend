package io.ten1010.aipubbrewerybackend.dockerfile.service;

import io.ten1010.aipubbrewerybackend.common.exception.ResourceNotFoundException;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.BuildContextFileResponse;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileMapper;
import io.ten1010.aipubbrewerybackend.dockerfile.entity.BuildContextFile;
import io.ten1010.aipubbrewerybackend.dockerfile.entity.Dockerfile;
import io.ten1010.aipubbrewerybackend.dockerfile.repository.BuildContextFileRepository;
import io.ten1010.aipubbrewerybackend.dockerfile.repository.DockerfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuildContextFileService {

    private final DockerfileRepository dockerfileRepository;
    private final BuildContextFileRepository fileRepository;
    private final DockerfileMapper mapper;

    @Value("${brewery.file-storage.base-path:./build-context-storage}")
    private String baseStoragePath;

    public List<BuildContextFileResponse> listFiles(Long dockerfileId) {
        findDockerfile(dockerfileId);
        return mapper.toFileResponseList(fileRepository.findByDockerfileId(dockerfileId));
    }

    @Transactional
    public BuildContextFileResponse uploadFile(Long dockerfileId, MultipartFile file, String targetPath) {
        Dockerfile dockerfile = findDockerfile(dockerfileId);

        validateTargetPath(targetPath);

        String storagePath = buildStoragePath(dockerfileId, targetPath);
        saveFileToDisk(file, storagePath);

        BuildContextFile entity = BuildContextFile.builder()
                .dockerfile(dockerfile)
                .fileName(file.getOriginalFilename())
                .targetPath(targetPath)
                .fileSize(file.getSize())
                .storagePath(storagePath)
                .build();

        dockerfile.getContextFiles().add(entity);
        dockerfileRepository.save(dockerfile);

        log.info("Uploaded context file: dockerfileId={}, targetPath={}, size={}",
                dockerfileId, targetPath, file.getSize());

        return mapper.toFileResponse(entity);
    }

    @Transactional
    public void deleteFile(Long dockerfileId, Long fileId) {
        Dockerfile dockerfile = findDockerfile(dockerfileId);
        BuildContextFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Context file not found: " + fileId));

        if (!file.getDockerfile().getId().equals(dockerfileId)) {
            throw new ResourceNotFoundException("Context file " + fileId + " does not belong to dockerfile " + dockerfileId);
        }

        deleteFileFromDisk(file.getStoragePath());
        dockerfile.getContextFiles().remove(file);
        dockerfileRepository.save(dockerfile);

        log.info("Deleted context file: dockerfileId={}, fileId={}", dockerfileId, fileId);
    }

    private Dockerfile findDockerfile(Long id) {
        return dockerfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dockerfile not found: " + id));
    }

    private void validateTargetPath(String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("targetPath is required");
        }
        if (targetPath.contains("..")) {
            throw new IllegalArgumentException("Path traversal is not allowed: " + targetPath);
        }
        if (targetPath.startsWith("/")) {
            throw new IllegalArgumentException("targetPath must be relative: " + targetPath);
        }
    }

    private String buildStoragePath(Long dockerfileId, String targetPath) {
        return baseStoragePath + "/dockerfiles/" + dockerfileId + "/" + targetPath;
    }

    private void saveFileToDisk(MultipartFile file, String storagePath) {
        try {
            Path path = Path.of(storagePath);
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + storagePath, e);
        }
    }

    private void deleteFileFromDisk(String storagePath) {
        try {
            Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException e) {
            log.warn("Failed to delete file from disk: {}", storagePath, e);
        }
    }

}
