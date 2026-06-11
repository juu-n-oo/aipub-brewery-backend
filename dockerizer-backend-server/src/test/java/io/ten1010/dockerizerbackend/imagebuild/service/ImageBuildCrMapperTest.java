package io.ten1010.dockerizerbackend.imagebuild.service;

import io.ten1010.dockerizerbackend.imagebuild.dto.ImageBuildResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CTL-8: 가장 회귀 위험이 큰 CR Map → 응답 매핑 로직을 k8s 없이 픽스처로 검증한다.
 */
class ImageBuildCrMapperTest {

    private static Map<String, Object> metadata(Map<String, String> labels, Map<String, String> annotations, String creationTimestamp) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "imagebuild-a1b2c3d4");
        metadata.put("namespace", "pjw");
        if (labels != null) {
            metadata.put("labels", labels);
        }
        if (annotations != null) {
            metadata.put("annotations", annotations);
        }
        if (creationTimestamp != null) {
            metadata.put("creationTimestamp", creationTimestamp);
        }
        return metadata;
    }

    @Test
    void toResponse_mapsAllFields_fromFullCr() {
        Map<String, Object> cr = new HashMap<>();
        cr.put("metadata", metadata(
                Map.of(
                        "aipub.ten1010.io/dockerfile-id", "1",
                        "aipub.ten1010.io/dockerfile-revision-id", "42",
                        "aipub.ten1010.io/username", "joonwoo"),
                Map.of("aipub.ten1010.io/base-image", "harbor/aipub/python:3.11"),
                "2026-04-18T00:00:00Z"));
        cr.put("spec", Map.of(
                "targetImage", "harbor/pjw/my-pytorch:v1.0",
                "dockerfileContent", "FROM python:3.11"));
        cr.put("status", Map.of(
                "phase", "Succeeded",
                "message", "Build completed successfully",
                "imageDigest", "sha256:abc123",
                "startTime", "2026-04-18T00:00:10Z",
                "completionTime", "2026-04-18T00:05:00Z"));

        ImageBuildResponse r = ImageBuildCrMapper.toResponse(cr);

        assertThat(r.getName()).isEqualTo("imagebuild-a1b2c3d4");
        assertThat(r.getNamespace()).isEqualTo("pjw");
        assertThat(r.getPhase()).isEqualTo("Succeeded");
        assertThat(r.getTargetImage()).isEqualTo("harbor/pjw/my-pytorch:v1.0");
        assertThat(r.getBaseImage()).isEqualTo("harbor/aipub/python:3.11");
        assertThat(r.getMessage()).isEqualTo("Build completed successfully");
        assertThat(r.getImageDigest()).isEqualTo("sha256:abc123");
        assertThat(r.getDockerfileId()).isEqualTo(1L);
        assertThat(r.getDockerfileRevisionId()).isEqualTo(42L);
        assertThat(r.getUsername()).isEqualTo("joonwoo");
        assertThat(r.getCreatedAt()).isEqualTo(Instant.parse("2026-04-18T00:00:00Z"));
        assertThat(r.getStartTime()).isEqualTo(Instant.parse("2026-04-18T00:00:10Z"));
        assertThat(r.getCompletionTime()).isEqualTo(Instant.parse("2026-04-18T00:05:00Z"));
    }

    @Test
    void toResponse_missingStatus_defaultsPhaseToPending() {
        Map<String, Object> cr = new HashMap<>();
        cr.put("metadata", metadata(Map.of(), Map.of(), "2026-04-18T00:00:00Z"));
        cr.put("spec", Map.of("targetImage", "harbor/pjw/img:v1"));
        // status 누락

        ImageBuildResponse r = ImageBuildCrMapper.toResponse(cr);

        assertThat(r.getPhase()).isEqualTo("Pending");
        assertThat(r.getMessage()).isNull();
        assertThat(r.getImageDigest()).isNull();
        assertThat(r.getStartTime()).isNull();
        assertThat(r.getCompletionTime()).isNull();
    }

    @Test
    void toResponse_missingLabelsAndAnnotations_yieldsNulls() {
        Map<String, Object> cr = new HashMap<>();
        cr.put("metadata", metadata(null, null, null));
        cr.put("spec", Map.of());
        cr.put("status", Map.of("phase", "Pending"));

        ImageBuildResponse r = ImageBuildCrMapper.toResponse(cr);

        assertThat(r.getDockerfileId()).isNull();
        assertThat(r.getDockerfileRevisionId()).isNull();
        assertThat(r.getUsername()).isNull();
        assertThat(r.getBaseImage()).isNull();
        assertThat(r.getTargetImage()).isNull();
        assertThat(r.getCreatedAt()).isNull();
    }

    @Test
    void toResponse_nonNumericLabel_parsesToNull() {
        Map<String, Object> cr = new HashMap<>();
        cr.put("metadata", metadata(
                Map.of("aipub.ten1010.io/dockerfile-id", "not-a-number"),
                Map.of(),
                "2026-04-18T00:00:00Z"));
        cr.put("status", Map.of("phase", "Building"));

        ImageBuildResponse r = ImageBuildCrMapper.toResponse(cr);

        assertThat(r.getDockerfileId()).isNull();
        assertThat(r.getPhase()).isEqualTo("Building");
    }

    @Test
    void toResponse_malformedTimestamp_parsesToNull() {
        Map<String, Object> cr = new HashMap<>();
        cr.put("metadata", metadata(Map.of(), Map.of(), "18-04-2026 not-iso"));
        cr.put("status", Map.of(
                "phase", "Succeeded",
                "completionTime", ""));

        ImageBuildResponse r = ImageBuildCrMapper.toResponse(cr);

        assertThat(r.getCreatedAt()).isNull();
        assertThat(r.getCompletionTime()).isNull();
    }

}
