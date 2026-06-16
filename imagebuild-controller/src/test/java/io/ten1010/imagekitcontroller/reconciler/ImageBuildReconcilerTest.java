package io.ten1010.imagekitcontroller.reconciler;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodList;
import io.ten1010.imagekitcontroller.cr.ImageBuildConstants;
import io.ten1010.imagekitcontroller.cr.ImageBuildResource;
import io.ten1010.imagekitcontroller.cr.ImageBuildSpec;
import io.ten1010.imagekitcontroller.cr.ImageBuildStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CTL-7: reconcile phase 상태머신과 CTL-1(create+409 멱등)/CTL-2(비-404 read → requeue) 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ImageBuildReconcilerTest {

    private static final String NS = "pjw";
    private static final String NAME = "imagebuild-a1b2c3d4";

    @Mock
    CoreV1Api coreV1Api;
    @Mock
    BatchV1Api batchV1Api;
    @Mock
    KanikoJobFactory jobFactory;
    @Mock
    ImageBuildStatusUpdater statusUpdater;
    @Mock
    Lister<ImageBuildResource> lister;
    @Mock
    Lister<ImageBuildResource> nsLister;

    ImageBuildReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new ImageBuildReconciler(coreV1Api, batchV1Api, jobFactory, statusUpdater, lister);
    }

    private ImageBuildResource cr(String phase) {
        return ImageBuildResource.builder()
                .metadata(new V1ObjectMeta().name(NAME).namespace(NS).uid("uid-1"))
                .spec(ImageBuildSpec.builder().targetImage("img:v1").dockerfileContent("FROM alpine").build())
                .status(phase == null ? null : ImageBuildStatus.builder().phase(phase).build())
                .build();
    }

    private void stubCache(ImageBuildResource cr) {
        when(lister.namespace(NS)).thenReturn(nsLister);
        when(nsLister.get(NAME)).thenReturn(cr);
    }

    private Request request() {
        return new Request(NS, NAME);
    }

    @SuppressWarnings("unchecked")
    private void stubConfigMapDelete() {
        var delReq = org.mockito.Mockito.mock(CoreV1Api.APIdeleteNamespacedConfigMapRequest.class);
        lenient().when(coreV1Api.deleteNamespacedConfigMap(anyString(), anyString())).thenReturn(delReq);
        try {
            lenient().when(delReq.execute()).thenReturn(null);
        } catch (ApiException ignored) {
        }
    }

    @Test
    void cacheMiss_returnsNoRequeue_andDoesNothing() {
        when(lister.namespace(NS)).thenReturn(nsLister);
        when(nsLister.get(NAME)).thenReturn(null);

        Result result = reconciler.reconcile(request());

        assertThat(result.isRequeue()).isFalse();
        verify(statusUpdater, never()).transitionTo(any(), anyString(), anyString());
    }

    @Test
    void terminalPhase_isNoOp() {
        stubCache(cr(ImageBuildConstants.PHASE_SUCCEEDED));

        Result result = reconciler.reconcile(request());

        assertThat(result.isRequeue()).isFalse();
        verify(statusUpdater, never()).transitionTo(any(), anyString(), anyString());
        verify(statusUpdater, never()).markSucceeded(any(), any());
        verify(statusUpdater, never()).markFailed(any(), anyString());
    }

    @Test
    void pending_createsConfigMap_thenTransitionsToPreparing() throws ApiException {
        ImageBuildResource cr = cr(null);
        stubCache(cr);
        when(jobFactory.createDockerfileConfigMap(cr)).thenReturn(new V1ConfigMap());
        var req = org.mockito.Mockito.mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
        when(coreV1Api.createNamespacedConfigMap(eq(NS), any())).thenReturn(req);
        when(req.execute()).thenReturn(new V1ConfigMap());

        reconciler.reconcile(request());

        verify(statusUpdater).transitionTo(cr, ImageBuildConstants.PHASE_PREPARING, "Dockerfile ConfigMap created, preparing Kaniko job");
        verify(statusUpdater, never()).markFailed(any(), anyString());
    }

    @Test
    void pending_conflictOnCreate_isIdempotent_andStillTransitions() throws ApiException {
        ImageBuildResource cr = cr(null);
        stubCache(cr);
        when(jobFactory.createDockerfileConfigMap(cr)).thenReturn(new V1ConfigMap());
        var req = org.mockito.Mockito.mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
        when(coreV1Api.createNamespacedConfigMap(eq(NS), any())).thenReturn(req);
        when(req.execute()).thenThrow(new ApiException(409, "AlreadyExists"));

        reconciler.reconcile(request());

        verify(statusUpdater).transitionTo(cr, ImageBuildConstants.PHASE_PREPARING,
                "Dockerfile ConfigMap created, preparing Kaniko job");
        verify(statusUpdater, never()).markFailed(any(), anyString());
    }

    @Test
    void pending_serverErrorOnCreate_marksFailed_andDoesNotTransition() throws ApiException {
        ImageBuildResource cr = cr(null);
        stubCache(cr);
        stubConfigMapDelete();
        when(jobFactory.createDockerfileConfigMap(cr)).thenReturn(new V1ConfigMap());
        var req = org.mockito.Mockito.mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
        when(coreV1Api.createNamespacedConfigMap(eq(NS), any())).thenReturn(req);
        when(req.execute()).thenThrow(new ApiException(500, "InternalError"));

        reconciler.reconcile(request());

        verify(statusUpdater).markFailed(eq(cr), anyString());
        verify(statusUpdater, never()).transitionTo(any(), anyString(), anyString());
    }

    @Test
    void preparing_createsJob_thenTransitionsToBuilding() throws ApiException {
        ImageBuildResource cr = cr(ImageBuildConstants.PHASE_PREPARING);
        stubCache(cr);
        when(jobFactory.createKanikoJob(cr)).thenReturn(new V1Job());
        var req = org.mockito.Mockito.mock(BatchV1Api.APIcreateNamespacedJobRequest.class);
        when(batchV1Api.createNamespacedJob(eq(NS), any())).thenReturn(req);
        when(req.execute()).thenReturn(new V1Job());

        reconciler.reconcile(request());

        verify(statusUpdater).transitionTo(cr, ImageBuildConstants.PHASE_BUILDING, "Kaniko job created, building image");
    }

    @Test
    void building_jobSucceeded_marksSucceeded() throws ApiException {
        ImageBuildResource cr = cr(ImageBuildConstants.PHASE_BUILDING);
        stubCache(cr);
        stubConfigMapDelete();
        V1Job job = new V1Job().status(new V1JobStatus().succeeded(1));
        var readReq = org.mockito.Mockito.mock(BatchV1Api.APIreadNamespacedJobRequest.class);
        when(batchV1Api.readNamespacedJob(NAME + "-job", NS)).thenReturn(readReq);
        when(readReq.execute()).thenReturn(job);
        // readImageDigest: 빈 Pod 목록 → digest null
        var podReq = org.mockito.Mockito.mock(CoreV1Api.APIlistNamespacedPodRequest.class);
        when(coreV1Api.listNamespacedPod(NS)).thenReturn(podReq);
        when(podReq.labelSelector(anyString())).thenReturn(podReq);
        when(podReq.execute()).thenReturn(new V1PodList().items(List.of()));

        reconciler.reconcile(request());

        verify(statusUpdater).markSucceeded(cr, null);
        verify(statusUpdater, never()).markFailed(any(), anyString());
    }

    @Test
    void building_jobFailed_marksFailed() throws ApiException {
        ImageBuildResource cr = cr(ImageBuildConstants.PHASE_BUILDING);
        stubCache(cr);
        stubConfigMapDelete();
        V1Job job = new V1Job().status(new V1JobStatus()
                .failed(1)
                .conditions(List.of(new V1JobCondition()
                        .type("Failed").status("True").reason("BackoffLimitExceeded").message("build error"))));
        var readReq = org.mockito.Mockito.mock(BatchV1Api.APIreadNamespacedJobRequest.class);
        when(batchV1Api.readNamespacedJob(NAME + "-job", NS)).thenReturn(readReq);
        when(readReq.execute()).thenReturn(job);

        reconciler.reconcile(request());

        verify(statusUpdater).markFailed(eq(cr), eq("build error"));
        verify(statusUpdater, never()).markSucceeded(any(), any());
    }

    @Test
    void building_nonNotFoundReadError_requeues() throws ApiException {
        ImageBuildResource cr = cr(ImageBuildConstants.PHASE_BUILDING);
        stubCache(cr);
        var readReq = org.mockito.Mockito.mock(BatchV1Api.APIreadNamespacedJobRequest.class);
        when(batchV1Api.readNamespacedJob(NAME + "-job", NS)).thenReturn(readReq);
        when(readReq.execute()).thenThrow(new ApiException(500, "InternalError"));

        Result result = reconciler.reconcile(request());

        assertThat(result.isRequeue()).isTrue();
        verify(statusUpdater, never()).markFailed(any(), anyString());
    }

    @Test
    void building_jobNotFound_marksFailed() throws ApiException {
        ImageBuildResource cr = cr(ImageBuildConstants.PHASE_BUILDING);
        stubCache(cr);
        var readReq = org.mockito.Mockito.mock(BatchV1Api.APIreadNamespacedJobRequest.class);
        when(batchV1Api.readNamespacedJob(NAME + "-job", NS)).thenReturn(readReq);
        when(readReq.execute()).thenThrow(new ApiException(404, "NotFound"));

        reconciler.reconcile(request());

        verify(statusUpdater).markFailed(cr, "Kaniko job not found");
    }

    @Test
    void extractFailureMessage_deadlineExceeded_returnsTimeoutHint() {
        ImageBuildResource cr = cr(ImageBuildConstants.PHASE_BUILDING);
        cr.getSpec().setBuildTimeoutSeconds(120);
        // resolveBuildTimeoutSeconds 는 실제 KanikoJobFactory 를 통해 계산되므로 실제 인스턴스를 주입
        ImageBuildReconciler real = new ImageBuildReconciler(coreV1Api, batchV1Api,
                new KanikoJobFactory(new io.ten1010.imagekitcontroller.config.ControllerProperties()),
                statusUpdater, lister);
        V1JobStatus status = new V1JobStatus()
                .failed(1)
                .conditions(List.of(new V1JobCondition()
                        .type("Failed").status("True").reason("DeadlineExceeded")));

        String msg = real.extractFailureMessage(status, cr);

        assertThat(msg).contains("제한 시간(2분)");
    }

    @Test
    void extractFailureMessage_noConditions_returnsGenericMessage() {
        ImageBuildReconciler real = new ImageBuildReconciler(coreV1Api, batchV1Api,
                new KanikoJobFactory(new io.ten1010.imagekitcontroller.config.ControllerProperties()),
                statusUpdater, lister);
        V1JobStatus status = new V1JobStatus().failed(2);

        String msg = real.extractFailureMessage(status, cr(ImageBuildConstants.PHASE_BUILDING));

        assertThat(msg).isEqualTo("Job failed (failed count: 2)");
    }

}
