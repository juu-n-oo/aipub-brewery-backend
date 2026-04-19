package io.ten1010.aipubbrewerycontroller.reconciler;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.util.Watch;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobWatcher {

    private static final String LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";
    private static final String MANAGER_NAME = "aipub-brewery-controller";
    private static final String LABEL_IMAGEBUILD_NAME = "brewery.aipub.ten1010.io/imagebuild-name";

    private final ApiClient apiClient;
    private final BatchV1Api batchV1Api;
    private final ImageBuildReconciler reconciler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        executor.submit(this::watchLoop);
        log.info("Job watcher started for managed-by={}", MANAGER_NAME);
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdownNow();
        log.info("Job watcher stopped");
    }

    private void watchLoop() {
        while (running) {
            try {
                startWatch();
            } catch (Exception e) {
                if (running) {
                    log.warn("Job watch connection lost, reconnecting in 5s: {}", e.getMessage());
                    sleep(5000);
                }
            }
        }
    }

    private void startWatch() throws Exception {
        String labelSelector = LABEL_MANAGED_BY + "=" + MANAGER_NAME;
        Call call = batchV1Api.listJobForAllNamespaces()
                .labelSelector(labelSelector)
                .watch(true)
                .buildCall(null);

        try (Watch<V1Job> watch = Watch.createWatch(
                apiClient,
                call,
                new TypeToken<Watch.Response<V1Job>>() {}.getType())) {

            for (Watch.Response<V1Job> event : watch) {
                if (!running) {
                    break;
                }
                handleEvent(event);
            }
        }
    }

    private void handleEvent(Watch.Response<V1Job> event) {
        try {
            V1Job job = event.object;
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

            log.debug("Job watch event: type={}, job={}/{}, imagebuild={}",
                    event.type, namespace, job.getMetadata().getName(), imageBuildName);

            if ("MODIFIED".equals(event.type)) {
                reconciler.reconcile(namespace, imageBuildName);
            }
        } catch (Exception e) {
            log.error("Error handling job watch event", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
