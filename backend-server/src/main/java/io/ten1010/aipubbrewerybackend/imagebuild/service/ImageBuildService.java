package io.ten1010.aipubbrewerybackend.imagebuild.service;

import com.google.gson.Gson;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.ten1010.aipubbrewerybackend.common.exception.ResourceNotFoundException;
import io.ten1010.aipubbrewerybackend.dockerfile.entity.Dockerfile;
import io.ten1010.aipubbrewerybackend.dockerfile.repository.DockerfileRepository;
import io.ten1010.aipubbrewerybackend.dockerfile.service.DockerfileValidator;
import io.ten1010.aipubbrewerybackend.imagebuild.cr.ImageBuildConstants;
import io.ten1010.aipubbrewerybackend.imagebuild.cr.ImageBuildCr;
import io.ten1010.aipubbrewerybackend.imagebuild.cr.ImageBuildSpec;
import io.ten1010.aipubbrewerybackend.imagebuild.cr.ImageBuildStatus;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildRequest;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageBuildService {

    private static final String LABEL_DOCKERFILE_ID = "brewery.aipub.ten1010.io/dockerfile-id";
    private static final String LABEL_USERNAME = "brewery.aipub.ten1010.io/username";

    private final DockerfileRepository dockerfileRepository;
    private final DockerfileValidator dockerfileValidator;
    private final CustomObjectsApi customObjectsApi;
    private final CoreV1Api coreV1Api;
    private final ApiClient apiClient;
    private final Gson gson = new Gson();
    private final ExecutorService logStreamExecutor = Executors.newCachedThreadPool();

    public ImageBuildResponse triggerBuild(ImageBuildRequest request) {
        Dockerfile dockerfile = dockerfileRepository.findById(request.getDockerfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Dockerfile not found: " + request.getDockerfileId()));

        dockerfileValidator.validate(dockerfile.getContent());

        String fullImage = request.getTargetImage() + ":" + request.getTag();
        String namespace = dockerfile.getProject();
        String crName = "imagebuild-" + UUID.randomUUID().toString().substring(0, 8);

        ImageBuildCr cr = ImageBuildCr.builder()
                .apiVersion(ImageBuildConstants.API_VERSION)
                .kind(ImageBuildConstants.KIND)
                .metadata(Map.of(
                        "name", crName,
                        "namespace", namespace,
                        "labels", Map.of(
                                LABEL_DOCKERFILE_ID, String.valueOf(dockerfile.getId()),
                                LABEL_USERNAME, dockerfile.getUsername()
                        )
                ))
                .spec(ImageBuildSpec.builder()
                        .dockerfileContent(dockerfile.getContent())
                        .targetImage(fullImage)
                        .pushSecretRef(request.getPushSecretRef())
                        .buildContextPvc(request.getBuildContextPvc())
                        .buildContextSubPath(request.getBuildContextSubPath())
                        .build())
                .build();

        try {
            customObjectsApi.createNamespacedCustomObject(
                    ImageBuildConstants.GROUP,
                    ImageBuildConstants.VERSION,
                    namespace,
                    ImageBuildConstants.PLURAL,
                    cr).execute();

            log.info("Created ImageBuild CR: {}/{}", namespace, crName);

            return ImageBuildResponse.builder()
                    .name(crName)
                    .namespace(namespace)
                    .phase(ImageBuildConstants.PHASE_PENDING)
                    .targetImage(fullImage)
                    .message("ImageBuild CR created successfully")
                    .dockerfileId(dockerfile.getId())
                    .username(dockerfile.getUsername())
                    .createdAt(Instant.now())
                    .build();
        } catch (ApiException e) {
            log.error("Failed to create ImageBuild CR: {}/{}, status={}, body={}",
                    namespace, crName, e.getCode(), e.getResponseBody(), e);
            throw new RuntimeException("Failed to create ImageBuild CR: " + e.getResponseBody(), e);
        }
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
            log.error("Failed to list ImageBuilds in namespace {}: code={}", namespace, e.getCode(), e);
            throw new RuntimeException("Failed to list builds: " + e.getResponseBody(), e);
        }
    }

    public ImageBuildResponse getBuildStatus(String namespace, String name) {
        Map<String, Object> crMap = getCrMap(namespace, name);
        return crMapToResponse(crMap);
    }

    public String getBuildLogs(String namespace, String name) {
        String podName = findBuildPodName(namespace, name);
        try {
            return coreV1Api.readNamespacedPodLog(podName, namespace).execute();
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new ResourceNotFoundException("Build pod not found: " + namespace + "/" + podName);
            }
            log.error("Failed to get build logs: {}/{}, status={}", namespace, podName, e.getCode(), e);
            throw new RuntimeException("Failed to get build logs: " + e.getResponseBody(), e);
        }
    }

    public SseEmitter streamBuildLogs(String namespace, String name) {
        String podName = findBuildPodName(namespace, name);
        SseEmitter emitter = new SseEmitter(300_000L); // 5분 timeout

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
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

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
            if (e.getCode() == 404) {
                throw new ResourceNotFoundException("ImageBuild not found: " + namespace + "/" + name);
            }
            throw new RuntimeException("Failed to get ImageBuild CR: " + e.getResponseBody(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ImageBuildResponse crMapToResponse(Map<String, Object> crMap) {
        Map<String, Object> metadata = (Map<String, Object>) crMap.get("metadata");
        Map<String, String> labels = (Map<String, String>) metadata.getOrDefault("labels", Map.of());
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
                .message(status.getMessage())
                .imageDigest(status.getImageDigest())
                .dockerfileId(parseLong(labels.get(LABEL_DOCKERFILE_ID)))
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

    private String findBuildPodName(String namespace, String name) {
        String jobName = name + "-job";
        String labelSelector = "job-name=" + jobName;
        try {
            V1PodList podList = coreV1Api.listNamespacedPod(namespace)
                    .labelSelector(labelSelector)
                    .execute();
            if (podList.getItems().isEmpty()) {
                throw new ResourceNotFoundException("Build pod not found for job: " + namespace + "/" + jobName);
            }
            return podList.getItems().getFirst().getMetadata().getName();
        } catch (ApiException e) {
            log.error("Failed to find build pod: {}/{}, status={}", namespace, jobName, e.getCode(), e);
            throw new RuntimeException("Failed to find build pod: " + e.getResponseBody(), e);
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
