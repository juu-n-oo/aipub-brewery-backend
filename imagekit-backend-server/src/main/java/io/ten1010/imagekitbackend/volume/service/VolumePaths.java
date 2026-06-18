package io.ten1010.imagekitbackend.volume.service;

import java.nio.file.Path;

/**
 * Volume 사용자 입력 경로/파일명의 검증·정규화 유틸. 브라우징({@link VolumeBrowser})과
 * 업로드({@link VolumeUploader}) 구현이 동일한 디렉토리 이탈 방지 규칙을 공유하도록 한 곳에 모은다.
 */
final class VolumePaths {

    private VolumePaths() {
    }

    /** {@code ..} 가 포함된 경로는 디렉토리 이탈 위험이 있어 거부한다. */
    static void validate(String path) {
        if (path != null && path.contains("..")) {
            throw new IllegalArgumentException("Path traversal is not allowed: " + path);
        }
    }

    /** 사용자 입력 경로를 "선행 슬래시 보장 + 후행 슬래시 제거"한 PVC 상대 경로로 정규화한다. */
    static String normalize(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 업로드 파일명을 안전한 단일 파일명으로 정규화한다.
     * 경로 구분자나 {@code ..} 를 포함하면 거부하여 디렉토리 이탈을 막는다.
     */
    static String resolveFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("Uploaded file name is required");
        }
        String name = Path.of(original).getFileName().toString();
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name: " + original);
        }
        return name;
    }

}
