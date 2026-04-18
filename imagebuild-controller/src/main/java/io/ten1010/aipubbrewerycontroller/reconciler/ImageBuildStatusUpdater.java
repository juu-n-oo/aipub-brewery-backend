package io.ten1010.aipubbrewerycontroller.reconciler;

import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.ten1010.aipubbrewerycontroller.config.ControllerProperties;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildConstants;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildResource;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildStatus;
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
    private final ControllerProperties properties;
    private final EventRecorder eventRecorder;
    private final Gson gson = new Gson();

    public void transitionTo(ImageBuildResource cr, String phase, String message) {
        String previousPhase = cr.getStatus() != null ? cr.getStatus().getPhase() : null;

        ImageBuildStatus status = ImageBuildStatus.builder()
                .phase(phase)
                .message(message)
                .build();

        if (ImageBuildConstants.PHASE_PREPARING.equals(phase)) {
            status.setStartTime(Instant.now().toString());
        }
        if (ImageBuildConstants.PHASE_SUCCEEDED.equals(phase) || ImageBuildConstants.PHASE_FAILED.equals(phase)) {
            status.setCompletionTime(Instant.now().toString());
        }

        patchStatus(cr.getNamespace(), cr.getName(), status);

        String eventReason = "PhaseTransition";
        String eventMessage = String.format("Phase changed: %s -> %s. %s",
                previousPhase != null ? previousPhase : "none", phase, message);

        if (ImageBuildConstants.PHASE_FAILED.equals(phase)) {
            eventRecorder.recordWarning(cr, eventReason, eventMessage);
        } else {
            eventRecorder.recordNormal(cr, eventReason, eventMessage);
        }
    }

    public void markSucceeded(ImageBuildResource cr, String imageDigest) {
        ImageBuildStatus status = ImageBuildStatus.builder()
                .phase(ImageBuildConstants.PHASE_SUCCEEDED)
                .completionTime(Instant.now().toString())
                .imageDigest(imageDigest)
                .message("Build completed successfully")
                .build();

        patchStatus(cr.getNamespace(), cr.getName(), status);
        eventRecorder.recordNormal(cr, "BuildSucceeded",
                "Image built and pushed successfully" + (imageDigest != null ? ": " + imageDigest : ""));
    }

    public void markFailed(ImageBuildResource cr, String message) {
        ImageBuildStatus status = ImageBuildStatus.builder()
                .phase(ImageBuildConstants.PHASE_FAILED)
                .completionTime(Instant.now().toString())
                .message(message)
                .build();

        patchStatus(cr.getNamespace(), cr.getName(), status);
        eventRecorder.recordWarning(cr, "BuildFailed", message);
    }

    private void patchStatus(String namespace, String name, ImageBuildStatus status) {
        Map<String, Object> patch = Map.of("status", gson.fromJson(gson.toJson(status), Map.class));
        try {
            customObjectsApi.patchNamespacedCustomObjectStatus(
                    properties.getGroup(),
                    properties.getVersion(),
                    namespace,
                    properties.getPlural(),
                    name,
                    patch).execute();
            log.info("Updated ImageBuild status: {}/{} -> {}", namespace, name, status.getPhase());
        } catch (ApiException e) {
            log.error("Failed to update ImageBuild status: {}/{}, code={}, body={}",
                    namespace, name, e.getCode(), e.getResponseBody(), e);
        }
    }

}
