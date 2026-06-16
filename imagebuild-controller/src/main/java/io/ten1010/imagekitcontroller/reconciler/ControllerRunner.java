package io.ten1010.imagekitcontroller.reconciler;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.informer.SharedInformerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * informer 들을 기동하고 Controller(블로킹 {@link Runnable})를 별도 스레드에서 구동한다.
 * 애플리케이션 종료 시 컨트롤러·informer 를 정지한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ControllerRunner {

    private final SharedInformerFactory sharedInformerFactory;
    private final Controller imageBuildController;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void start() {
        sharedInformerFactory.startAllRegisteredInformers();
        executor.submit(imageBuildController::run);
        log.info("ImageBuild controller started (SharedIndexInformer + workqueue)");
    }

    @PreDestroy
    public void stop() {
        imageBuildController.shutdown();
        sharedInformerFactory.stopAllRegisteredInformers();
        executor.shutdownNow();
        log.info("ImageBuild controller stopped");
    }

}
