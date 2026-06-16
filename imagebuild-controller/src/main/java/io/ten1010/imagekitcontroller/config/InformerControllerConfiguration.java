package io.ten1010.imagekitcontroller.config;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.builder.DefaultControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.ten1010.imagekitcontroller.cr.ImageBuildResource;
import io.ten1010.imagekitcontroller.cr.ImageBuildResourceList;
import io.ten1010.imagekitcontroller.reconciler.ImageBuildReconciler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * client-java-extended 의 informer + workqueue + Controller 로 컨트롤러를 구성한다.
 * <p>
 * 직접 watch 루프(edge-triggered) 를 대체한다. resyncPeriod 마다 캐시된 전 CR 이 재조정되어
 * 단일 이벤트 유실 시에도 빌드가 phase 에 영구 정지하지 않는다(C-1). 워크큐가 동일 키의 동시
 * 처리를 막아 두 소스(CR/Job)에서 들어온 reconcile 이 직렬화된다(C-3). reconcile 은 informer
 * 캐시(Lister)에서 CR 을 읽어 매 이벤트 live GET 을 제거한다(C-4).
 */
@Configuration
@Slf4j
public class InformerControllerConfiguration {

    private static final String LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";
    private static final String MANAGER_NAME = "imagekit-controller";
    private static final String LABEL_IMAGEBUILD_NAME = "aipub.ten1010.io/imagebuild-name";
    private static final String CONTROLLER_NAME = "imagebuild-controller";

    @Bean
    public SharedInformerFactory sharedInformerFactory(ApiClient apiClient) {
        return new SharedInformerFactory(apiClient);
    }

    @Bean
    public SharedIndexInformer<ImageBuildResource> imageBuildInformer(
            SharedInformerFactory factory, ApiClient apiClient, ControllerProperties properties) {
        GenericKubernetesApi<ImageBuildResource, ImageBuildResourceList> api =
                new GenericKubernetesApi<>(
                        ImageBuildResource.class,
                        ImageBuildResourceList.class,
                        properties.getGroup(),
                        properties.getVersion(),
                        properties.getPlural(),
                        apiClient);
        return factory.sharedIndexInformerFor(
                api, ImageBuildResource.class, resyncMillis(properties));
    }

    @Bean
    public SharedIndexInformer<V1Job> jobInformer(
            SharedInformerFactory factory, BatchV1Api batchV1Api, ControllerProperties properties) {
        String labelSelector = LABEL_MANAGED_BY + "=" + MANAGER_NAME;
        return factory.sharedIndexInformerFor(
                (CallGeneratorParams params) -> batchV1Api.listJobForAllNamespaces()
                        .labelSelector(labelSelector)
                        .resourceVersion(params.resourceVersion)
                        .timeoutSeconds(params.timeoutSeconds)
                        .watch(params.watch)
                        .buildCall(null),
                V1Job.class, V1JobList.class, resyncMillis(properties));
    }

    @Bean
    public Lister<ImageBuildResource> imageBuildLister(SharedIndexInformer<ImageBuildResource> imageBuildInformer) {
        return new Lister<>(imageBuildInformer.getIndexer());
    }

    @Bean
    public Controller imageBuildController(
            SharedInformerFactory factory,
            SharedIndexInformer<ImageBuildResource> imageBuildInformer,
            SharedIndexInformer<V1Job> jobInformer,
            ImageBuildReconciler reconciler,
            ControllerProperties properties) {

        long resyncMillis = resyncMillis(properties);
        Duration resyncPeriod = Duration.ofSeconds(properties.getResyncPeriodSeconds());

        DefaultControllerBuilder builder = ControllerBuilder.defaultBuilder(factory)
                .watch(workQueue -> {
                    // 2차 리소스(Job): Job 이벤트를 소유 ImageBuild 의 Request 로 변환해 같은 워크큐에 넣는다.
                    // primary 워크큐를 그대로 공유해야 컨트롤러가 같은 큐를 drain 한다.
                    jobInformer.addEventHandlerWithResyncPeriod(jobEventHandler(workQueue), resyncMillis);
                    return ControllerBuilder
                            .controllerWatchBuilder(ImageBuildResource.class, workQueue)
                            .withResyncPeriod(resyncPeriod)
                            .withWorkQueueKeyFunc(cr ->
                                    new Request(cr.getMetadata().getNamespace(), cr.getMetadata().getName()))
                            .build();
                })
                .withReconciler(reconciler)
                .withName(CONTROLLER_NAME)
                .withWorkerCount(properties.getWorkerCount())
                .withReadyFunc(() -> imageBuildInformer.hasSynced() && jobInformer.hasSynced());

        return builder.build();
    }

    private ResourceEventHandler<V1Job> jobEventHandler(WorkQueue<Request> queue) {
        return new ResourceEventHandler<>() {
            @Override
            public void onAdd(V1Job job) {
                enqueueOwner(job, queue);
            }

            @Override
            public void onUpdate(V1Job oldJob, V1Job newJob) {
                enqueueOwner(newJob, queue);
            }

            @Override
            public void onDelete(V1Job job, boolean deletedFinalStateUnknown) {
                enqueueOwner(job, queue);
            }
        };
    }

    private void enqueueOwner(V1Job job, WorkQueue<Request> queue) {
        if (job == null || job.getMetadata() == null) {
            return;
        }
        Map<String, String> labels = job.getMetadata().getLabels();
        if (labels == null) {
            return;
        }
        String imageBuildName = labels.get(LABEL_IMAGEBUILD_NAME);
        String namespace = job.getMetadata().getNamespace();
        if (imageBuildName == null || namespace == null) {
            return;
        }
        queue.add(new Request(namespace, imageBuildName));
    }

    private long resyncMillis(ControllerProperties properties) {
        return properties.getResyncPeriodSeconds() * 1000L;
    }

}
