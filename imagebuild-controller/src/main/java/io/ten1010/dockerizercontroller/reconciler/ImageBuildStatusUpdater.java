package io.ten1010.dockerizercontroller.reconciler;

import com.google.gson.Gson;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.PatchUtils;
import io.ten1010.dockerizercontroller.config.ControllerProperties;
import io.ten1010.dockerizercontroller.cr.ImageBuildConstants;
import io.ten1010.dockerizercontroller.cr.ImageBuildResource;
import io.ten1010.dockerizercontroller.cr.ImageBuildStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageBuildStatusUpdater {

    private final CustomObjectsApi customObjectsApi;
    private final ApiClient apiClient;
    private final ControllerProperties properties;
    private final EventRecorder eventRecorder;
    private final Gson gson = new Gson();

    /**
     * 비-terminal phase 전환(Pending→Preparing, Preparing→Building) 전용.
     * <p>
     * CTL-6: terminal(Succeeded/Failed) 은 전적으로 {@link #markSucceeded}/{@link #markFailed} 가 구동하므로
     * 여기서 completionTime 을 다루던 분기는 도달 불가였다 — 제거했다.
     */
    public void transitionTo(ImageBuildResource cr, String phase, String message) {
        String previousPhase = cr.getStatus() != null ? cr.getStatus().getPhase() : null;

        ImageBuildStatus status = ImageBuildStatus.builder()
                .phase(phase)
                .message(message)
                .build();

        if (ImageBuildConstants.PHASE_PREPARING.equals(phase)) {
            status.setStartTime(Instant.now().toString());
        }

        applyStatus(cr, status);

        String eventReason = "PhaseTransition";
        String eventMessage = String.format("Phase changed: %s -> %s. %s",
                previousPhase != null ? previousPhase : "none", phase, message);
        eventRecorder.recordNormal(cr, eventReason, eventMessage);
    }

    public void markSucceeded(ImageBuildResource cr, String imageDigest) {
        applyStatus(cr, ImageBuildStatus.builder()
                .phase(ImageBuildConstants.PHASE_SUCCEEDED)
                .completionTime(Instant.now().toString())
                .imageDigest(imageDigest)
                .message("Build completed successfully")
                .build());
        eventRecorder.recordNormal(cr, "BuildSucceeded",
                "Image built and pushed successfully" + (imageDigest != null ? ": " + imageDigest : ""));
    }

    public void markFailed(ImageBuildResource cr, String message) {
        applyStatus(cr, ImageBuildStatus.builder()
                .phase(ImageBuildConstants.PHASE_FAILED)
                .completionTime(Instant.now().toString())
                .message(message)
                .build());
        eventRecorder.recordWarning(cr, "BuildFailed", message);
    }

    /** CTL-6: 모든 status 쓰기의 단일 진입점. */
    private void applyStatus(ImageBuildResource cr, ImageBuildStatus status) {
        patchStatus(cr.getNamespace(), cr.getName(), status);
    }

    private void patchStatus(String namespace, String name, ImageBuildStatus status) {
        Map<String, Object> patch = Map.of("status", gson.fromJson(gson.toJson(status), Map.class));
        String patchJson = gson.toJson(patch);
        try {
            PatchUtils.patch(
                    Object.class,
                    () -> customObjectsApi.patchNamespacedCustomObjectStatus(
                            properties.getGroup(),
                            properties.getVersion(),
                            namespace,
                            properties.getPlural(),
                            name,
                            new V1Patch(patchJson)).buildCall(null),
                    V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH,
                    apiClient);
            log.info("Updated ImageBuild status: {}/{} -> {}", namespace, name, status.getPhase());
        } catch (ApiException e) {
            log.error("Failed to update ImageBuild status: {}/{}, code={}, body={}",
                    namespace, name, e.getCode(), e.getResponseBody(), e);
        }
    }

}
