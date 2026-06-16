package io.ten1010.imagekitcontroller.reconciler;

import io.kubernetes.client.openapi.models.*;
import io.ten1010.imagekitcontroller.config.ControllerProperties;
import io.ten1010.imagekitcontroller.cr.ImageBuildConstants;
import io.ten1010.imagekitcontroller.cr.ImageBuildResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KanikoJobFactory {

    private static final String LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";
    private static final String LABEL_IMAGEBUILD_NAME = "aipub.ten1010.io/imagebuild-name";
    private static final String MANAGER_NAME = "imagekit-controller";
    private static final String DOCKERFILE_VOLUME = "dockerfile";
    private static final String DOCKER_CONFIG_VOLUME = "docker-config";
    private static final String BUILD_CONTEXT_VOLUME = "build-context";
    public static final String KANIKO_CONTAINER_NAME = "kaniko";
    // Kaniko --digest-file 출력 경로. 컨테이너 terminationMessagePath 와 동일하게 두어
    // k8s 가 파일 내용(digest)을 containerStatus.terminated.message 로 캡처하게 한다.
    private static final String DIGEST_FILE_PATH = "/dev/termination-log";

    // CTL-4: 산재하던 apiVersion/kind/policy·마운트 경로·키·접미사 리터럴을 named 상수로 승격.
    private static final String API_VERSION_CORE = "v1";
    private static final String KIND_CONFIGMAP = "ConfigMap";
    private static final String API_VERSION_BATCH = "batch/v1";
    private static final String KIND_JOB = "Job";
    private static final String RESTART_POLICY_NEVER = "Never";
    private static final String TERMINATION_MESSAGE_POLICY_FILE = "File";
    // ConfigMap data 키이자 마운트 시 파일명(둘은 반드시 같아야 한다).
    private static final String DOCKERFILE_NAME = "Dockerfile";
    private static final String SUFFIX_DOCKERFILE = "-dockerfile";
    private static final String SUFFIX_JOB = "-job";
    // Kaniko 컨테이너 마운트 경로
    private static final String MOUNT_KANIKO_CONFIG = "/kaniko-config";
    private static final String MOUNT_BUILD_CONTEXT = "/build-context";
    private static final String MOUNT_WORKSPACE = "/workspace";
    private static final String MOUNT_DOCKER_CONFIG = "/kaniko/.docker";

    private final ControllerProperties properties;

    public V1ConfigMap createDockerfileConfigMap(ImageBuildResource cr) {
        return new V1ConfigMap()
                .apiVersion(API_VERSION_CORE)
                .kind(KIND_CONFIGMAP)
                .metadata(new V1ObjectMeta()
                        .name(configMapName(cr.getName()))
                        .namespace(cr.getNamespace())
                        .labels(commonLabels(cr.getName()))
                        .ownerReferences(List.of(ownerReference(cr))))
                .data(Map.of(DOCKERFILE_NAME, cr.getSpec().getDockerfileContent()));
    }

    public V1Job createKanikoJob(ImageBuildResource cr) {
        String namespace = cr.getNamespace();
        String pushSecretName = resolvePushSecretName(namespace, cr.getSpec().getPushSecretRef());
        boolean hasBuildContext = cr.getSpec().getBuildContextPvc() != null
                && !cr.getSpec().getBuildContextPvc().isBlank();

        List<V1Volume> volumes = new ArrayList<>();
        volumes.add(dockerfileVolume(cr.getName()));
        volumes.add(dockerConfigVolume(pushSecretName));
        if (hasBuildContext) {
            volumes.add(buildContextVolume(cr.getSpec().getBuildContextPvc()));
        }

        return new V1Job()
                .apiVersion(API_VERSION_BATCH)
                .kind(KIND_JOB)
                .metadata(new V1ObjectMeta()
                        .name(jobName(cr.getName()))
                        .namespace(namespace)
                        .labels(commonLabels(cr.getName()))
                        .ownerReferences(List.of(ownerReference(cr))))
                .spec(new V1JobSpec()
                        .backoffLimit(0)
                        .activeDeadlineSeconds((long) resolveBuildTimeoutSeconds(cr))
                        .ttlSecondsAfterFinished(properties.getJobTtlSeconds())
                        .template(new V1PodTemplateSpec()
                                .metadata(new V1ObjectMeta()
                                        .labels(commonLabels(cr.getName())))
                                .spec(new V1PodSpec()
                                        .restartPolicy(RESTART_POLICY_NEVER)
                                        .containers(List.of(kanikoContainer(cr, hasBuildContext)))
                                        .volumes(volumes))));
    }

    // CTL-3: 컨테이너 정의는 args/mounts 조립을 전담 헬퍼로 위임해 분기 중복(특히 dockerfile 마운트)을 제거한다.
    private V1Container kanikoContainer(ImageBuildResource cr, boolean hasBuildContext) {
        return new V1Container()
                .name(KANIKO_CONTAINER_NAME)
                .image(properties.getKanikoImage())
                .args(buildArgs(cr, hasBuildContext))
                .volumeMounts(buildMounts(cr, hasBuildContext))
                // digest 를 termination message 로 캡처하기 위한 명시 설정(기본값과 동일하나 명시)
                .terminationMessagePath(DIGEST_FILE_PATH)
                .terminationMessagePolicy(TERMINATION_MESSAGE_POLICY_FILE);
    }

    List<String> buildArgs(ImageBuildResource cr, boolean hasBuildContext) {
        List<String> args = new ArrayList<>();
        if (hasBuildContext) {
            // PVC is the build context at /build-context (read-only);
            // /workspace is reserved for Kaniko's image layer writes
            args.add("--dockerfile=" + MOUNT_KANIKO_CONFIG + "/" + DOCKERFILE_NAME);
            args.add("--context=dir://" + MOUNT_BUILD_CONTEXT);
        } else {
            // No PVC: Dockerfile ConfigMap IS the build context
            args.add("--dockerfile=" + MOUNT_WORKSPACE + "/" + DOCKERFILE_NAME);
            args.add("--context=dir://" + MOUNT_WORKSPACE);
        }

        args.add("--destination=" + cr.getSpec().getTargetImage());
        args.add("--cache=false");
        // C-7: insecure / TLS skip 은 토글(기본 true = 내부 Harbor self-signed 대상 현행 유지)
        if (properties.isRegistryInsecure()) {
            args.add("--insecure");
        }
        if (properties.isRegistrySkipTlsVerify()) {
            args.add("--skip-tls-verify");
        }
        // C-6: 빌드된 이미지 digest 를 termination message 로 출력 → 컨트롤러가 Pod 에서 읽어 status 에 기록
        args.add("--digest-file=" + DIGEST_FILE_PATH);
        args.addAll(labelArgs(cr.getSpec().getImageLabels()));
        return args;
    }

    List<V1VolumeMount> buildMounts(ImageBuildResource cr, boolean hasBuildContext) {
        List<V1VolumeMount> mounts = new ArrayList<>();
        if (hasBuildContext) {
            mounts.add(new V1VolumeMount()
                    .name(DOCKERFILE_VOLUME)
                    .mountPath(MOUNT_KANIKO_CONFIG));

            V1VolumeMount contextMount = new V1VolumeMount()
                    .name(BUILD_CONTEXT_VOLUME)
                    .mountPath(MOUNT_BUILD_CONTEXT);
            String subPath = cr.getSpec().getBuildContextSubPath();
            if (subPath != null && !subPath.isBlank()) {
                // Strip leading slash for k8s subPath
                contextMount.subPath(subPath.startsWith("/") ? subPath.substring(1) : subPath);
            }
            mounts.add(contextMount);
        } else {
            mounts.add(new V1VolumeMount()
                    .name(DOCKERFILE_VOLUME)
                    .mountPath(MOUNT_WORKSPACE));
        }

        mounts.add(new V1VolumeMount()
                .name(DOCKER_CONFIG_VOLUME)
                .mountPath(MOUNT_DOCKER_CONFIG));
        return mounts;
    }

    /**
     * OCI/provenance 라벨을 이미지 config 에 baking 하기 위한 {@code --label key=value} args.
     * 키 순서를 정렬해 동일 입력에 대해 결정적인 args 를 생성한다.
     */
    List<String> labelArgs(Map<String, String> imageLabels) {
        if (imageLabels == null) {
            return List.of();
        }
        return imageLabels.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "--label=" + e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()))
                .toList();
    }

    private V1Volume dockerfileVolume(String crName) {
        return new V1Volume()
                .name(DOCKERFILE_VOLUME)
                .configMap(new V1ConfigMapVolumeSource()
                        .name(configMapName(crName)));
    }

    private V1Volume dockerConfigVolume(String pushSecretName) {
        return new V1Volume()
                .name(DOCKER_CONFIG_VOLUME)
                .secret(new V1SecretVolumeSource()
                        .secretName(pushSecretName)
                        .items(List.of(
                                new V1KeyToPath()
                                        .key(".dockerconfigjson")
                                        .path("config.json"))));
    }

    private V1Volume buildContextVolume(String pvcName) {
        return new V1Volume()
                .name(BUILD_CONTEXT_VOLUME)
                .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)
                        .readOnly(true));
    }

    /**
     * Job 의 activeDeadlineSeconds 로 쓸 빌드 제한 시간(초)을 해석한다.
     * CR spec.buildTimeoutSeconds 가 양수면 그 값을, 아니면 컨트롤러 기본값을 쓴다.
     */
    public int resolveBuildTimeoutSeconds(ImageBuildResource cr) {
        Integer specTimeout = cr.getSpec() != null ? cr.getSpec().getBuildTimeoutSeconds() : null;
        if (specTimeout != null && specTimeout > 0) {
            return specTimeout;
        }
        return properties.getBuildTimeoutSeconds();
    }

    private V1OwnerReference ownerReference(ImageBuildResource cr) {
        // informer 캐시에서 읽은 객체는 top-level apiVersion/kind 가 비어 있을 수 있으므로
        // CR 식별자 상수를 사용한다(ownerReference 가 비면 GC 가 깨진다).
        return new V1OwnerReference()
                .apiVersion(ImageBuildConstants.API_VERSION)
                .kind(ImageBuildConstants.KIND)
                .name(cr.getName())
                .uid(cr.getUid())
                .controller(true)
                .blockOwnerDeletion(true);
    }

    private String resolvePushSecretName(String namespace, String pushSecretRef) {
        if (pushSecretRef != null && !pushSecretRef.isBlank()) {
            return pushSecretRef;
        }
        return ImageBuildConstants.IMAGE_REGISTRY_SECRET_PREFIX + namespace;
    }

    private Map<String, String> commonLabels(String crName) {
        return Map.of(
                LABEL_MANAGED_BY, MANAGER_NAME,
                LABEL_IMAGEBUILD_NAME, crName);
    }

    private String configMapName(String crName) {
        return crName + SUFFIX_DOCKERFILE;
    }

    private String jobName(String crName) {
        return crName + SUFFIX_JOB;
    }

}
