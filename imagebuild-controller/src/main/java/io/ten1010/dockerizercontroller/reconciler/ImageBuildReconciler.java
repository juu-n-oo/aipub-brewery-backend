package io.ten1010.dockerizercontroller.reconciler;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.ten1010.dockerizercontroller.cr.ImageBuildConstants;
import io.ten1010.dockerizercontroller.cr.ImageBuildResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ImageBuild CR 을 reconcile 하는 워크큐 기반 컨트롤러 reconciler.
 * <p>
 * CR 은 live GET 대신 informer 캐시({@link Lister})에서 읽는다. phase 기반 단방향 상태머신이며
 * 멱등(존재 검사 + 409 무시)하므로, resync/재시작으로 reconcile 이 여러 번 돌아도 안전하다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageBuildReconciler implements Reconciler {

    private static final String LABEL_IMAGEBUILD_NAME = "aipub.ten1010.io/imagebuild-name";

    private final CoreV1Api coreV1Api;
    private final BatchV1Api batchV1Api;
    private final KanikoJobFactory jobFactory;
    private final ImageBuildStatusUpdater statusUpdater;
    private final Lister<ImageBuildResource> lister;

    @Override
    public Result reconcile(Request request) {
        String namespace = request.getNamespace();
        String name = request.getName();

        ImageBuildResource cr = lister.namespace(namespace).get(name);
        if (cr == null) {
            log.debug("ImageBuild {}/{} not found in cache, skipping", namespace, name);
            return new Result(false);
        }

        try {
            String phase = currentPhase(cr);
            switch (phase) {
                case ImageBuildConstants.PHASE_PENDING -> handlePending(cr);
                case ImageBuildConstants.PHASE_PREPARING -> handlePreparing(cr);
                case ImageBuildConstants.PHASE_BUILDING -> handleBuilding(cr);
                case ImageBuildConstants.PHASE_SUCCEEDED, ImageBuildConstants.PHASE_FAILED ->
                        log.debug("ImageBuild {}/{} already in terminal phase: {}", namespace, name, phase);
                default -> log.warn("ImageBuild {}/{} has unknown phase: {}", namespace, name, phase);
            }
            return new Result(false);
        } catch (RuntimeException e) {
            log.error("Reconcile error for ImageBuild {}/{}, requeueing", namespace, name, e);
            return new Result(true);
        }
    }

    /**
     * Pending → Preparing: ConfigMap 생성
     * <p>
     * CTL-1: 존재 여부를 미리 read 하지 않는다(비-404 read 오류를 "없음" 으로 삼키는 false-negative 제거).
     * create 를 그대로 시도하고 409(이미 존재)만 멱등 성공으로 흡수한다.
     */
    private void handlePending(ImageBuildResource cr) {
        String namespace = cr.getNamespace();
        String configMapName = cr.getName() + "-dockerfile";

        try {
            V1ConfigMap configMap = jobFactory.createDockerfileConfigMap(cr);
            coreV1Api.createNamespacedConfigMap(namespace, configMap).execute();
            log.info("Created ConfigMap: {}/{}", namespace, configMapName);
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.debug("ConfigMap {}/{} already exists", namespace, configMapName);
            } else {
                log.error("Failed to create ConfigMap: {}/{}", namespace, configMapName, e);
                statusUpdater.markFailed(cr, "Failed to create ConfigMap: " + e.getResponseBody());
                deleteDockerfileConfigMap(cr);
                return;
            }
        }

        statusUpdater.transitionTo(cr, ImageBuildConstants.PHASE_PREPARING,
                "Dockerfile ConfigMap created, preparing Kaniko job");
    }

    /**
     * Preparing → Building: Kaniko Job 생성
     * <p>
     * CTL-1: handlePending 과 동일하게 사전 존재 검사 없이 create + 409 멱등으로 처리한다.
     */
    private void handlePreparing(ImageBuildResource cr) {
        String namespace = cr.getNamespace();
        String jobName = cr.getName() + "-job";

        try {
            V1Job job = jobFactory.createKanikoJob(cr);
            batchV1Api.createNamespacedJob(namespace, job).execute();
            log.info("Created Kaniko Job: {}/{}", namespace, jobName);
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.debug("Job {}/{} already exists", namespace, jobName);
            } else {
                log.error("Failed to create Job: {}/{}", namespace, jobName, e);
                statusUpdater.markFailed(cr, "Failed to create Kaniko job: " + e.getResponseBody());
                deleteDockerfileConfigMap(cr);
                return;
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
                String imageDigest = readImageDigest(namespace, cr.getName());
                statusUpdater.markSucceeded(cr, imageDigest);
                deleteDockerfileConfigMap(cr);
                log.info("ImageBuild succeeded: {}/{} (digest={})", namespace, cr.getName(), imageDigest);
            } else if (jobStatus.getFailed() != null && jobStatus.getFailed() > 0) {
                String failMsg = extractFailureMessage(jobStatus, cr);
                statusUpdater.markFailed(cr, failMsg);
                deleteDockerfileConfigMap(cr);
                log.info("ImageBuild failed: {}/{} - {}", namespace, cr.getName(), failMsg);
            }
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                statusUpdater.markFailed(cr, "Kaniko job not found");
            } else {
                // CTL-2: 종료 감지 구간의 일시 read 실패는 no-op 으로 흘리지 않고 requeue 한다
                // (reconcile 의 catch(RuntimeException) 가 Result(true) 로 재시도).
                throw new RuntimeException(
                        "Failed to read Job status for " + namespace + "/" + jobName, e);
            }
        }
    }

    /**
     * C-6: 빌드된 이미지의 digest 를 읽는다. Kaniko 가 {@code --digest-file} 로 출력한 값을 k8s 가
     * 빌드 Pod 의 kaniko 컨테이너 {@code terminated.message} 로 캡처한다. 미취득(Pod GC/빈 값/오류)
     * 시 null 을 반환하며, 빌드 성공 자체는 유지한다.
     */
    private String readImageDigest(String namespace, String crName) {
        try {
            V1PodList pods = coreV1Api.listNamespacedPod(namespace)
                    .labelSelector(LABEL_IMAGEBUILD_NAME + "=" + crName)
                    .execute();
            for (V1Pod pod : pods.getItems()) {
                if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                    continue;
                }
                for (V1ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                    if (KanikoJobFactory.KANIKO_CONTAINER_NAME.equals(cs.getName())
                            && cs.getState() != null
                            && cs.getState().getTerminated() != null) {
                        String message = cs.getState().getTerminated().getMessage();
                        if (message != null && !message.isBlank()) {
                            return message.trim();
                        }
                    }
                }
            }
        } catch (ApiException e) {
            log.warn("Failed to read image digest for {}/{}: code={}", namespace, crName, e.getCode());
        }
        return null;
    }

    /**
     * C-9: 빌드 Dockerfile ConfigMap 을 삭제한다(terminal phase 도달 시 1회). Dockerfile 본문은 DB 와
     * CR {@code spec.dockerfileContent} 에 보존되므로 손실이 없다. CM 수명을 CR 수명에서 분리해
     * 빌드 이력 보존 시에도 CM 이 무한 누적되지 않게 한다. 404(이미 없음)는 무시한다.
     */
    private void deleteDockerfileConfigMap(ImageBuildResource cr) {
        String namespace = cr.getNamespace();
        String configMapName = cr.getName() + "-dockerfile";
        try {
            coreV1Api.deleteNamespacedConfigMap(configMapName, namespace).execute();
            log.info("Deleted Dockerfile ConfigMap: {}/{}", namespace, configMapName);
        } catch (ApiException e) {
            if (e.getCode() != 404) {
                log.warn("Failed to delete Dockerfile ConfigMap {}/{}: code={}",
                        namespace, configMapName, e.getCode());
            }
        }
    }

    private String currentPhase(ImageBuildResource cr) {
        if (cr.getStatus() == null || cr.getStatus().getPhase() == null) {
            return ImageBuildConstants.PHASE_PENDING;
        }
        return cr.getStatus().getPhase();
    }

    // CTL-7: 순수 분기 로직이라 단위 테스트 대상 — 같은 패키지 테스트에서 접근하도록 package-private.
    String extractFailureMessage(V1JobStatus jobStatus, ImageBuildResource cr) {
        List<V1JobCondition> conditions = jobStatus.getConditions();
        if (conditions != null) {
            for (V1JobCondition condition : conditions) {
                if ("Failed".equals(condition.getType()) && "True".equals(condition.getStatus())) {
                    // 제한 시간 초과(activeDeadlineSeconds)로 kill 된 경우 — 빌드 에러(비정상 종료)와 구분해
                    // "느려서 끊긴 것일 수 있음" 단서를 준다(slow-vs-hung 구분은 불가하나 timeout-vs-error 는 구분).
                    if ("DeadlineExceeded".equals(condition.getReason())) {
                        int minutes = jobFactory.resolveBuildTimeoutSeconds(cr) / 60;
                        return String.format(
                                "빌드가 제한 시간(%d분)을 초과하여 중단되었습니다. 이미지가 무거워 정상적으로 오래 걸리는 경우라면, 빌드 실행 시 제한 시간을 늘려 다시 시도하세요.",
                                minutes);
                    }
                    return condition.getMessage() != null ? condition.getMessage() : "Job failed";
                }
            }
        }
        return "Job failed (failed count: " + jobStatus.getFailed() + ")";
    }

}
