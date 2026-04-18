package io.ten1010.aipubbrewerycontroller.reconciler;

import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.ten1010.aipubbrewerycontroller.config.ControllerProperties;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildConstants;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageBuildReconciler {

    private final CustomObjectsApi customObjectsApi;
    private final CoreV1Api coreV1Api;
    private final BatchV1Api batchV1Api;
    private final ControllerProperties properties;
    private final KanikoJobFactory jobFactory;
    private final ImageBuildStatusUpdater statusUpdater;
    private final Gson gson = new Gson();

    public void reconcile(String namespace, String name) {
        ImageBuildResource cr = getImageBuild(namespace, name);
        if (cr == null) {
            log.debug("ImageBuild {}/{} not found, skipping", namespace, name);
            return;
        }

        String phase = currentPhase(cr);

        switch (phase) {
            case ImageBuildConstants.PHASE_PENDING -> handlePending(cr);
            case ImageBuildConstants.PHASE_PREPARING -> handlePreparing(cr);
            case ImageBuildConstants.PHASE_BUILDING -> handleBuilding(cr);
            case ImageBuildConstants.PHASE_SUCCEEDED, ImageBuildConstants.PHASE_FAILED ->
                    log.debug("ImageBuild {}/{} already in terminal phase: {}", namespace, name, phase);
            default -> log.warn("ImageBuild {}/{} has unknown phase: {}", namespace, name, phase);
        }
    }

    /**
     * Pending → Preparing: ConfigMap 생성
     */
    private void handlePending(ImageBuildResource cr) {
        String namespace = cr.getNamespace();
        String configMapName = cr.getName() + "-dockerfile";

        if (!configMapExists(namespace, configMapName)) {
            try {
                V1ConfigMap configMap = jobFactory.createDockerfileConfigMap(cr);
                coreV1Api.createNamespacedConfigMap(namespace, configMap).execute();
                log.info("Created ConfigMap: {}/{}", namespace, configMapName);
            } catch (ApiException e) {
                if (e.getCode() != 409) {
                    log.error("Failed to create ConfigMap: {}/{}", namespace, configMapName, e);
                    statusUpdater.markFailed(cr, "Failed to create ConfigMap: " + e.getResponseBody());
                    return;
                }
                log.debug("ConfigMap {}/{} already exists", namespace, configMapName);
            }
        }

        statusUpdater.transitionTo(cr, ImageBuildConstants.PHASE_PREPARING,
                "Dockerfile ConfigMap created, preparing Kaniko job");
    }

    /**
     * Preparing → Building: Kaniko Job 생성
     */
    private void handlePreparing(ImageBuildResource cr) {
        String namespace = cr.getNamespace();
        String jobName = cr.getName() + "-job";

        if (!jobExists(namespace, jobName)) {
            try {
                V1Job job = jobFactory.createKanikoJob(cr);
                batchV1Api.createNamespacedJob(namespace, job).execute();
                log.info("Created Kaniko Job: {}/{}", namespace, jobName);
            } catch (ApiException e) {
                if (e.getCode() != 409) {
                    log.error("Failed to create Job: {}/{}", namespace, jobName, e);
                    statusUpdater.markFailed(cr, "Failed to create Kaniko job: " + e.getResponseBody());
                    return;
                }
                log.debug("Job {}/{} already exists", namespace, jobName);
            }
        }

        statusUpdater.transitionTo(cr, ImageBuildConstants.PHASE_BUILDING,
                "Kaniko job created, building image");
    }

    /**
     * Building → Succeeded / Failed: Job 완료 감지
     */
    private void handleBuilding(ImageBuildResource cr) {
        String namespace = cr.getNamespace();
        String jobName = cr.getName() + "-job";

        try {
            V1Job job = batchV1Api.readNamespacedJob(jobName, namespace).execute();
            V1JobStatus jobStatus = job.getStatus();
            if (jobStatus == null) {
                return;
            }

            if (jobStatus.getSucceeded() != null && jobStatus.getSucceeded() > 0) {
                statusUpdater.markSucceeded(cr, null);
                log.info("ImageBuild succeeded: {}/{}", namespace, cr.getName());
            } else if (jobStatus.getFailed() != null && jobStatus.getFailed() > 0) {
                String failMsg = extractFailureMessage(jobStatus);
                statusUpdater.markFailed(cr, failMsg);
                log.info("ImageBuild failed: {}/{} - {}", namespace, cr.getName(), failMsg);
            }
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                statusUpdater.markFailed(cr, "Kaniko job not found");
            } else {
                log.error("Failed to check Job status: {}/{}", namespace, jobName, e);
            }
        }
    }

    private ImageBuildResource getImageBuild(String namespace, String name) {
        try {
            Object result = customObjectsApi.getNamespacedCustomObject(
                    properties.getGroup(),
                    properties.getVersion(),
                    namespace,
                    properties.getPlural(),
                    name).execute();
            return gson.fromJson(gson.toJson(result), ImageBuildResource.class);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return null;
            }
            log.error("Failed to get ImageBuild: {}/{}", namespace, name, e);
            return null;
        }
    }

    private String currentPhase(ImageBuildResource cr) {
        if (cr.getStatus() == null || cr.getStatus().getPhase() == null) {
            return ImageBuildConstants.PHASE_PENDING;
        }
        return cr.getStatus().getPhase();
    }

    private boolean configMapExists(String namespace, String name) {
        try {
            coreV1Api.readNamespacedConfigMap(name, namespace).execute();
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private boolean jobExists(String namespace, String name) {
        try {
            batchV1Api.readNamespacedJob(name, namespace).execute();
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private String extractFailureMessage(V1JobStatus jobStatus) {
        List<V1JobCondition> conditions = jobStatus.getConditions();
        if (conditions != null) {
            for (V1JobCondition condition : conditions) {
                if ("Failed".equals(condition.getType()) && "True".equals(condition.getStatus())) {
                    return condition.getMessage() != null ? condition.getMessage() : "Job failed";
                }
            }
        }
        return "Job failed (failed count: " + jobStatus.getFailed() + ")";
    }

}
