package io.ten1010.imagekitcontroller.reconciler;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.ten1010.imagekitcontroller.cr.ImageBuildConstants;
import io.ten1010.imagekitcontroller.cr.ImageBuildResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventRecorder {

    private static final String COMPONENT = "imagebuild-controller";

    private final CoreV1Api coreV1Api;

    public void recordNormal(ImageBuildResource cr, String reason, String message) {
        record(cr, "Normal", reason, message);
    }

    public void recordWarning(ImageBuildResource cr, String reason, String message) {
        record(cr, "Warning", reason, message);
    }

    private void record(ImageBuildResource cr, String type, String reason, String message) {
        String namespace = cr.getNamespace();
        OffsetDateTime now = OffsetDateTime.now();

        // CTL-9: modern(events.k8s.io) 관례로 통일 — eventTime + reportingComponent/reportingInstance + action.
        // legacy firstTimestamp/lastTimestamp 와 source.component 는 중복이라 설정하지 않는다.
        CoreV1Event event = new CoreV1Event()
                .apiVersion("v1")
                .kind("Event")
                .metadata(new V1ObjectMeta()
                        .name(cr.getName() + "." + UUID.randomUUID().toString().substring(0, 8))
                        .namespace(namespace))
                .involvedObject(new V1ObjectReference()
                        .apiVersion(ImageBuildConstants.API_VERSION)
                        .kind(ImageBuildConstants.KIND)
                        .name(cr.getName())
                        .namespace(namespace)
                        .uid(cr.getUid()))
                .reason(reason)
                .message(message)
                .type(type)
                .eventTime(now)
                .reportingComponent(COMPONENT)
                .reportingInstance(COMPONENT)
                .action(reason);

        try {
            coreV1Api.createNamespacedEvent(namespace, event).execute();
            log.debug("Recorded event: {}/{} reason={} message={}", namespace, cr.getName(), reason, message);
        } catch (ApiException e) {
            log.warn("Failed to record event for {}/{}: code={}", namespace, cr.getName(), e.getCode());
        }
    }

}
