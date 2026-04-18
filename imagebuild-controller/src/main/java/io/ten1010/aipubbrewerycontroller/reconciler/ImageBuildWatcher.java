package io.ten1010.aipubbrewerycontroller.reconciler;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;
import io.ten1010.aipubbrewerycontroller.config.ControllerProperties;
import io.ten1010.aipubbrewerycontroller.cr.ImageBuildResource;
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
public class ImageBuildWatcher {

    private final ApiClient apiClient;
    private final CustomObjectsApi customObjectsApi;
    private final ControllerProperties properties;
    private final ImageBuildReconciler reconciler;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        executor.submit(this::watchLoop);
        log.info("ImageBuild watcher started for {}/{}/{}",
                properties.getGroup(), properties.getVersion(), properties.getPlural());
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdownNow();
        log.info("ImageBuild watcher stopped");
    }

    private void watchLoop() {
        while (running) {
            try {
                startWatch();
            } catch (Exception e) {
                if (running) {
                    log.warn("Watch connection lost, reconnecting in 5s: {}", e.getMessage());
                    sleep(5000);
                }
            }
        }
    }

    private void startWatch() throws Exception {
        Call call = customObjectsApi.listCustomObjectForAllNamespaces(
                        properties.getGroup(),
                        properties.getVersion(),
                        properties.getPlural())
                .watch(true)
                .buildCall(null);

        try (Watch<Object> watch = Watch.createWatch(
                apiClient,
                call,
                new TypeToken<Watch.Response<Object>>() {}.getType())) {

            for (Watch.Response<Object> event : watch) {
                if (!running) {
                    break;
                }
                handleEvent(event);
            }
        }
    }

    private void handleEvent(Watch.Response<Object> event) {
        try {
            String type = event.type;
            ImageBuildResource cr = gson.fromJson(gson.toJson(event.object), ImageBuildResource.class);

            if (cr == null || cr.getName() == null || cr.getNamespace() == null) {
                return;
            }

            log.debug("Watch event: type={}, name={}/{}", type, cr.getNamespace(), cr.getName());

            switch (type) {
                case "ADDED", "MODIFIED" -> reconciler.reconcile(cr.getNamespace(), cr.getName());
                case "DELETED" -> log.info("ImageBuild deleted: {}/{}", cr.getNamespace(), cr.getName());
                default -> log.debug("Ignoring watch event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling watch event", e);
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
