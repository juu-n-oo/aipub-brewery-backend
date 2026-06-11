package io.ten1010.dockerizerbackend.imagebuild.service;

import com.google.gson.Gson;
import io.ten1010.dockerizerbackend.imagebuild.cr.ImageBuildConstants;
import io.ten1010.dockerizerbackend.imagebuild.cr.ImageBuildSpec;
import io.ten1010.dockerizerbackend.imagebuild.cr.ImageBuildStatus;
import io.ten1010.dockerizerbackend.imagebuild.dto.ImageBuildResponse;

import java.time.Instant;
import java.util.Map;

/**
 * ImageBuild CR 의 무타입 {@code Map<String,Object>} 표현을 {@link ImageBuildResponse} 로 매핑한다.
 * <p>
 * CTL-8: 가장 회귀 위험이 큰 파싱 로직(status/spec 역직렬화·타임스탬프·숫자 라벨 파싱)을
 * {@code ImageBuildService} 에서 분리해 k8s 의존 없이 픽스처 기반 단위 테스트가 가능하게 한다.
 */
final class ImageBuildCrMapper {

    private static final String LABEL_DOCKERFILE_ID = "aipub.ten1010.io/dockerfile-id";
    private static final String LABEL_REVISION_ID = "aipub.ten1010.io/dockerfile-revision-id";
    private static final String LABEL_USERNAME = "aipub.ten1010.io/username";
    // base image 는 registry/repo:tag 형태라 label 값 제약(63자, '/' ':' 불가)에 맞지 않아 annotation 으로 저장
    private static final String ANNOTATION_BASE_IMAGE = "aipub.ten1010.io/base-image";

    private static final Gson GSON = new Gson();

    private ImageBuildCrMapper() {
    }

    @SuppressWarnings("unchecked")
    static ImageBuildResponse toResponse(Map<String, Object> crMap) {
        Map<String, Object> metadata = (Map<String, Object>) crMap.get("metadata");
        Map<String, String> labels = (Map<String, String>) metadata.getOrDefault("labels", Map.of());
        Map<String, String> annotations = (Map<String, String>) metadata.getOrDefault("annotations", Map.of());
        ImageBuildStatus status = parseStatus(crMap);
        ImageBuildSpec spec = parseSpec(crMap);

        String name = (String) metadata.get("name");
        String namespace = (String) metadata.get("namespace");
        String creationTimestamp = (String) metadata.get("creationTimestamp");

        return ImageBuildResponse.builder()
                .name(name)
                .namespace(namespace)
                .phase(status.getPhase() != null ? status.getPhase() : ImageBuildConstants.PHASE_PENDING)
                .targetImage(spec.getTargetImage())
                .baseImage(annotations.get(ANNOTATION_BASE_IMAGE))
                .message(status.getMessage())
                .imageDigest(status.getImageDigest())
                .dockerfileId(parseLong(labels.get(LABEL_DOCKERFILE_ID)))
                .dockerfileRevisionId(parseLong(labels.get(LABEL_REVISION_ID)))
                .username(labels.get(LABEL_USERNAME))
                .createdAt(parseInstant(creationTimestamp))
                .startTime(parseInstant(status.getStartTime()))
                .completionTime(parseInstant(status.getCompletionTime()))
                .build();
    }

    private static ImageBuildStatus parseStatus(Map<String, Object> crMap) {
        Object statusObj = crMap.get("status");
        if (statusObj == null) {
            return ImageBuildStatus.builder().build();
        }
        return GSON.fromJson(GSON.toJson(statusObj), ImageBuildStatus.class);
    }

    private static ImageBuildSpec parseSpec(Map<String, Object> crMap) {
        Object specObj = crMap.get("spec");
        if (specObj == null) {
            return ImageBuildSpec.builder().build();
        }
        return GSON.fromJson(GSON.toJson(specObj), ImageBuildSpec.class);
    }

    private static Instant parseInstant(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateTimeStr);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
