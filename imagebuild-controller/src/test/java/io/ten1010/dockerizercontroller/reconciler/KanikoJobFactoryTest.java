package io.ten1010.dockerizercontroller.reconciler;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.ten1010.dockerizercontroller.config.ControllerProperties;
import io.ten1010.dockerizercontroller.cr.ImageBuildResource;
import io.ten1010.dockerizercontroller.cr.ImageBuildSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CTL-7/CTL-3: KanikoJobFactory 의 args/mount/volume 조립을 k8s 없이 순수 단위 테스트한다.
 */
class KanikoJobFactoryTest {

    private final ControllerProperties properties = new ControllerProperties();
    private final KanikoJobFactory factory = new KanikoJobFactory(properties);

    private static ImageBuildResource cr(ImageBuildSpec spec) {
        return ImageBuildResource.builder()
                .metadata(new V1ObjectMeta().name("imagebuild-a1b2c3d4").namespace("pjw").uid("uid-1"))
                .spec(spec)
                .build();
    }

    @Test
    void buildArgs_withoutBuildContext_usesWorkspace() {
        ImageBuildResource cr = cr(ImageBuildSpec.builder()
                .targetImage("harbor/pjw/img:v1")
                .dockerfileContent("FROM alpine")
                .build());

        List<String> args = factory.buildArgs(cr, false);

        assertThat(args).contains(
                "--dockerfile=/workspace/Dockerfile",
                "--context=dir:///workspace",
                "--destination=harbor/pjw/img:v1",
                "--cache=false",
                "--digest-file=/dev/termination-log");
        // 기본 properties: insecure/skip-tls-verify 둘 다 true
        assertThat(args).contains("--insecure", "--skip-tls-verify");
    }

    @Test
    void buildArgs_withBuildContext_usesKanikoConfigAndBuildContextDir() {
        ImageBuildResource cr = cr(ImageBuildSpec.builder()
                .targetImage("harbor/pjw/img:v1")
                .buildContextPvc("ctx-pvc")
                .build());

        List<String> args = factory.buildArgs(cr, true);

        assertThat(args).contains(
                "--dockerfile=/kaniko-config/Dockerfile",
                "--context=dir:///build-context");
    }

    @Test
    void buildArgs_registryFlagsRespectProperties() {
        properties.setRegistryInsecure(false);
        properties.setRegistrySkipTlsVerify(false);
        ImageBuildResource cr = cr(ImageBuildSpec.builder().targetImage("img:v1").build());

        List<String> args = factory.buildArgs(cr, false);

        assertThat(args).doesNotContain("--insecure", "--skip-tls-verify");
    }

    @Test
    void labelArgs_sortedByKey_deterministicOrder() {
        // 정렬되지 않은 입력
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("zeta", "1");
        labels.put("alpha", "2");
        labels.put("  ", "blank-key-dropped");
        labels.put("mid", null);

        List<String> args = factory.labelArgs(labels);

        assertThat(args).containsExactly(
                "--label=alpha=2",
                "--label=mid=",
                "--label=zeta=1");
    }

    @Test
    void labelArgs_null_returnsEmpty() {
        assertThat(factory.labelArgs(null)).isEmpty();
    }

    @Test
    void buildMounts_withBuildContext_stripsLeadingSlashOnSubPath() {
        ImageBuildResource cr = cr(ImageBuildSpec.builder()
                .targetImage("img:v1")
                .buildContextPvc("ctx-pvc")
                .buildContextSubPath("/sub/dir")
                .build());

        List<V1VolumeMount> mounts = factory.buildMounts(cr, true);

        V1VolumeMount contextMount = mounts.stream()
                .filter(m -> "build-context".equals(m.getName()))
                .findFirst().orElseThrow();
        assertThat(contextMount.getMountPath()).isEqualTo("/build-context");
        assertThat(contextMount.getSubPath()).isEqualTo("sub/dir");
        // dockerfile + docker-config 마운트도 존재
        assertThat(mounts).extracting(V1VolumeMount::getMountPath)
                .contains("/kaniko-config", "/kaniko/.docker");
    }

    @Test
    void buildMounts_withoutBuildContext_mountsDockerfileAtWorkspace() {
        ImageBuildResource cr = cr(ImageBuildSpec.builder().targetImage("img:v1").build());

        List<V1VolumeMount> mounts = factory.buildMounts(cr, false);

        assertThat(mounts).extracting(V1VolumeMount::getMountPath)
                .containsExactly("/workspace", "/kaniko/.docker");
    }

    @Test
    void resolveBuildTimeoutSeconds_prefersSpecWhenPositive() {
        ImageBuildResource cr = cr(ImageBuildSpec.builder().buildTimeoutSeconds(120).build());
        assertThat(factory.resolveBuildTimeoutSeconds(cr)).isEqualTo(120);
    }

    @Test
    void resolveBuildTimeoutSeconds_fallsBackToDefaultWhenNullOrNonPositive() {
        assertThat(factory.resolveBuildTimeoutSeconds(cr(ImageBuildSpec.builder().build())))
                .isEqualTo(properties.getBuildTimeoutSeconds());
        assertThat(factory.resolveBuildTimeoutSeconds(cr(ImageBuildSpec.builder().buildTimeoutSeconds(0).build())))
                .isEqualTo(properties.getBuildTimeoutSeconds());
    }

    @Test
    void createKanikoJob_setsBackoffLimitZeroAndOwnerReferenceAndLabels() {
        ImageBuildResource cr = cr(ImageBuildSpec.builder().targetImage("img:v1").build());

        V1Job job = factory.createKanikoJob(cr);

        assertThat(job.getSpec().getBackoffLimit()).isZero();
        assertThat(job.getMetadata().getName()).isEqualTo("imagebuild-a1b2c3d4-job");
        assertThat(job.getMetadata().getOwnerReferences()).hasSize(1);
        assertThat(job.getMetadata().getOwnerReferences().get(0).getController()).isTrue();
        assertThat(job.getMetadata().getLabels())
                .containsEntry("aipub.ten1010.io/imagebuild-name", "imagebuild-a1b2c3d4")
                .containsEntry("app.kubernetes.io/managed-by", "dockerizer-controller");
        // build context 없으면 volume 2개(dockerfile, docker-config)
        assertThat(job.getSpec().getTemplate().getSpec().getVolumes())
                .extracting(V1Volume::getName)
                .containsExactlyInAnyOrder("dockerfile", "docker-config");
        V1Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(container.getName()).isEqualTo("kaniko");
        assertThat(container.getTerminationMessagePolicy()).isEqualTo("File");
    }

}
