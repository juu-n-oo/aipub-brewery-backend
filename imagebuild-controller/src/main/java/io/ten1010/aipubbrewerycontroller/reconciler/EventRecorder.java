package io.ten1010.aipubbrewerycontroller.reconciler;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1EventSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildConstants;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildResource;
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
                .action(reason)
                .firstTimestamp(now)
                .lastTimestamp(now)
                .source(new V1EventSource().component(COMPONENT));

        try {
            coreV1Api.createNamespacedEvent(namespace, event).execute();
            log.debug("Recorded event: {}/{} reason={} message={}", namespace, cr.getName(), reason, message);
        } catch (ApiException e) {
            log.warn("Failed to record event for {}/{}: code={}", namespace, cr.getName(), e.getCode());
        }
    }

}
