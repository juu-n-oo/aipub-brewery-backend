package io.ten1010.aipubbrewerycontroller.reconciler;

import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipubbrewerycontroller.config.ControllerProperties;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildConstants;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KanikoJobFactory {

    private static final String LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";
    private static final String LABEL_IMAGEBUILD_NAME = "brewery.aipub.ten1010.io/imagebuild-name";
    private static final String MANAGER_NAME = "aipub-brewery-controller";
    private static final String DOCKERFILE_VOLUME = "dockerfile";
    private static final String DOCKER_CONFIG_VOLUME = "docker-config";
    private static final String BUILD_CONTEXT_VOLUME = "build-context";
    private static final String KANIKO_CONTAINER_NAME = "kaniko";

    private final ControllerProperties properties;

    public V1ConfigMap createDockerfileConfigMap(ImageBuildResource cr) {
        return new V1ConfigMap()
                .apiVersion("v1")
                .kind("ConfigMap")
                .metadata(new V1ObjectMeta()
                        .name(configMapName(cr.getName()))
                        .namespace(cr.getNamespace())
                        .labels(commonLabels(cr.getName()))
                        .ownerReferences(List.of(ownerReference(cr))))
                .data(Map.of("Dockerfile", cr.getSpec().getDockerfileContent()));
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
                .apiVersion("batch/v1")
                .kind("Job")
                .metadata(new V1ObjectMeta()
                        .name(jobName(cr.getName()))
                        .namespace(namespace)
                        .labels(commonLabels(cr.getName()))
                        .ownerReferences(List.of(ownerReference(cr))))
                .spec(new V1JobSpec()
                        .backoffLimit(0)
                        .ttlSecondsAfterFinished(3600)
                        .template(new V1PodTemplateSpec()
                                .metadata(new V1ObjectMeta()
                                        .labels(commonLabels(cr.getName())))
                                .spec(new V1PodSpec()
                                        .restartPolicy("Never")
                                        .containers(List.of(kanikoContainer(cr, hasBuildContext)))
                                        .volumes(volumes))));
    }

    private V1Container kanikoContainer(ImageBuildResource cr, boolean hasBuildContext) {
        List<String> args = new ArrayList<>();
        List<V1VolumeMount> mounts = new ArrayList<>();

        if (hasBuildContext) {
            // PVC is the build context at /workspace; Dockerfile is mounted separately
            args.add("--dockerfile=/kaniko-config/Dockerfile");
            args.add("--context=dir:///workspace");

            mounts.add(new V1VolumeMount()
                    .name(DOCKERFILE_VOLUME)
                    .mountPath("/kaniko-config"));

            V1VolumeMount contextMount = new V1VolumeMount()
                    .name(BUILD_CONTEXT_VOLUME)
                    .mountPath("/workspace");
            String subPath = cr.getSpec().getBuildContextSubPath();
            if (subPath != null && !subPath.isBlank()) {
                // Strip leading slash for k8s subPath
                contextMount.subPath(subPath.startsWith("/") ? subPath.substring(1) : subPath);
            }
            mounts.add(contextMount);
        } else {
            // No PVC: Dockerfile ConfigMap IS the build context
            args.add("--dockerfile=/workspace/Dockerfile");
            args.add("--context=dir:///workspace");

            mounts.add(new V1VolumeMount()
                    .name(DOCKERFILE_VOLUME)
                    .mountPath("/workspace"));
        }

        args.add("--destination=" + cr.getSpec().getTargetImage());
        args.add("--cache=false");
        args.add("--insecure");
        args.add("--skip-tls-verify");

        mounts.add(new V1VolumeMount()
                .name(DOCKER_CONFIG_VOLUME)
                .mountPath("/kaniko/.docker"));

        return new V1Container()
                .name(KANIKO_CONTAINER_NAME)
                .image(properties.getKanikoImage())
                .args(args)
                .volumeMounts(mounts);
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

    private V1OwnerReference ownerReference(ImageBuildResource cr) {
        return new V1OwnerReference()
                .apiVersion(cr.getApiVersion())
                .kind(cr.getKind())
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
        return crName + "-dockerfile";
    }

    private String jobName(String crName) {
        return crName + "-job";
    }

}
