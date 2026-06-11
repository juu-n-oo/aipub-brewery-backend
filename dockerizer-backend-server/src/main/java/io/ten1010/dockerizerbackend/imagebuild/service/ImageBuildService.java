package io.ten1010.dockerizerbackend.imagebuild.service;

import com.google.gson.Gson;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.ten1010.dockerizerbackend.common.exception.K8sExceptions;
import io.ten1010.dockerizerbackend.common.exception.ResourceNotFoundException;
import io.ten1010.dockerizerbackend.imagebuild.cr.ImageBuildConstants;
import io.ten1010.dockerizerbackend.imagebuild.cr.ImageBuildSpec;
import io.ten1010.dockerizerbackend.imagebuild.cr.ImageBuildStatus;
import io.ten1010.dockerizerbackend.imagebuild.dto.ImageBuildResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageBuildService {

    private static final String LABEL_DOCKERFILE_ID = "aipub.ten1010.io/dockerfile-id";
    private static final String LABEL_REVISION_ID = "aipub.ten1010.io/dockerfile-revision-id";
    private static final String LABEL_USERNAME = "aipub.ten1010.io/username";
    // base image 는 registry/repo:tag 형태라 label 값 제약(63자, '/' ':' 불가)에 맞지 않아 annotation 으로 저장
    private static final String ANNOTATION_BASE_IMAGE = "aipub.ten1010.io/base-image";
    // 컨트롤러가 빌드 Pod 에 부여하는 라벨. 암묵적 job-name 대신 이 라벨로 Pod 를 선택한다(SRV-6).
    private static final String LABEL_IMAGEBUILD_NAME = "aipub.ten1010.io/imagebuild-name";
    // 동시 SSE 로그 스트림 상한. 무제한 cached pool 대신 bounded pool 로 스레드 고갈을 막는다(SRV-8).
    private static final int MAX_CONCURRENT_LOG_STREAMS = 32;

    private final CustomObjectsApi customObjectsApi;
    private final CoreV1Api coreV1Api;
    private final ApiClient apiClient;
    // OpenSearch fallback 은 dockerizer.opensearch.enabled=true 일 때만 Bean 이 존재.
    // 미존재(비활성) 시 getIfAvailable() 이 null → 기존 404 동작 유지.
    private final ObjectProvider<OpenSearchBuildLogClient> openSearchBuildLogClient;
    private final Gson gson = new Gson();
    // SynchronousQueue + max thread → cached pool 의 응답성을 유지하되 동시 스트림 수를 상한한다.
    // 상한 초과 시 submit 이 RejectedExecutionException 을 던져 호출부가 emitter 를 에러 종료한다.
    private final ExecutorService logStreamExecutor = new ThreadPoolExecutor(
            0, MAX_CONCURRENT_LOG_STREAMS, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "build-log-stream");
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    void shutdownLogStreamExecutor() {
        logStreamExecutor.shutdownNow();
    }

    @SuppressWarnings("unchecked")
    public List<ImageBuildResponse> listBuilds(String namespace) {
        try {
            Object result = customObjectsApi.listNamespacedCustomObject(
                    ImageBuildConstants.GROUP,
                    ImageBuildConstants.VERSION,
                    namespace,
                    ImageBuildConstants.PLURAL).execute();

            Map<String, Object> resultMap = (Map<String, Object>) result;
            List<Map<String, Object>> items = (List<Map<String, Object>>) resultMap.get("items");

            return items.stream()
                    .map(this::crMapToResponse)
                    .toList();
        } catch (ApiException e) {
            throw K8sExceptions.translateNon404(e, "ImageBuilds in namespace " + namespace);
        }
    }

    public ImageBuildResponse getBuildStatus(String namespace, String name) {
        Map<String, Object> crMap = getCrMap(namespace, name);
        return crMapToResponse(crMap);
    }

    public String getBuildLogs(String namespace, String name) {
        // 1) 살아있는 Kaniko Pod 의 stdout 우선. Pod 부재(GC)이거나 readLog 가 404 면 OpenSearch fallback.
        Optional<String> podName = findBuildPodName(namespace, name);
        if (podName.isPresent()) {
            try {
                return coreV1Api.readNamespacedPodLog(podName.get(), namespace).execute();
            } catch (ApiException e) {
                if (e.getCode() != 404) {
                    throw K8sExceptions.translateNon404(e, "build logs for " + namespace + "/" + podName.get());
                }
                // 404: Pod 가 막 GC 된 경계 케이스 → 아래 fallback 으로 진행.
            }
        }

        // 2) OpenSearch fallback (비활성이면 404).
        OpenSearchBuildLogClient client = openSearchBuildLogClient.getIfAvailable();
        if (client == null) {
            throw new ResourceNotFoundException("Build logs not found: " + namespace + "/" + name);
        }
        // 0건이면 client 가 ResourceNotFoundException 을 던져 404 로 전파.
        return client.fetchPodLogs(namespace, name);
    }

    public SseEmitter streamBuildLogs(String namespace, String name) {
        String podName = findBuildPodName(namespace, name)
                .orElseThrow(() -> new ResourceNotFoundException("Build pod not found: " + namespace + "/" + name));
        SseEmitter emitter = new SseEmitter(300_000L); // 5분 timeout

        try {
            logStreamExecutor.submit(() -> {
                try {
                    PodLogs podLogs = new PodLogs(apiClient);
                    InputStream logStream = podLogs.streamNamespacedPodLog(
                            namespace, podName, null, null, null, true);

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(logStream))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            emitter.send(SseEmitter.event().data(line));
                        }
                    }

                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("Log stream ended for {}/{}: {}", namespace, podName, e.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    } catch (Exception sendFailure) {
                        // emitter 가 이미 끊긴 상태 → 원래 스트림 오류로 종료.
                        emitter.completeWithError(e);
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 동시 스트림 상한(MAX_CONCURRENT_LOG_STREAMS) 초과.
            log.warn("Log stream rejected (concurrency limit) for {}/{}", namespace, name);
            emitter.completeWithError(e);
            return emitter;
        }

        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> log.debug("SSE log stream completed: {}/{}", namespace, podName));

        return emitter;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCrMap(String namespace, String name) {
        try {
            return (Map<String, Object>) customObjectsApi.getNamespacedCustomObject(
                    ImageBuildConstants.GROUP,
                    ImageBuildConstants.VERSION,
                    namespace,
                    ImageBuildConstants.PLURAL,
                    name).execute();
        } catch (ApiException e) {
            throw K8sExceptions.translate(e, "ImageBuild " + namespace + "/" + name);
        }
    }

    @SuppressWarnings("unchecked")
    private ImageBuildResponse crMapToResponse(Map<String, Object> crMap) {
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

    private ImageBuildStatus parseStatus(Map<String, Object> crMap) {
        Object statusObj = crMap.get("status");
        if (statusObj == null) {
            return ImageBuildStatus.builder().build();
        }
        return gson.fromJson(gson.toJson(statusObj), ImageBuildStatus.class);
    }

    private ImageBuildSpec parseSpec(Map<String, Object> crMap) {
        Object specObj = crMap.get("spec");
        if (specObj == null) {
            return ImageBuildSpec.builder().build();
        }
        return gson.fromJson(gson.toJson(specObj), ImageBuildSpec.class);
    }

    private Instant parseInstant(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateTimeStr);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 빌드 Pod 이름을 찾는다. 컨트롤러가 Pod 에 부여하는 {@link #LABEL_IMAGEBUILD_NAME} 라벨로 선택한다
     * (암묵적 {@code job-name}/{@code -job} 접미사 규약에 의존하지 않음, SRV-6). Pod 가 없으면 빈 Optional.
     */
    private Optional<String> findBuildPodName(String namespace, String name) {
        String labelSelector = LABEL_IMAGEBUILD_NAME + "=" + name;
        try {
            V1PodList podList = coreV1Api.listNamespacedPod(namespace)
                    .labelSelector(labelSelector)
                    .execute();
            return podList.getItems().stream()
                    .findFirst()
                    .map(pod -> pod.getMetadata().getName());
        } catch (ApiException e) {
            throw K8sExceptions.translateNon404(e, "build pods for " + namespace + "/" + name);
        }
    }

    private Long parseLong(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
