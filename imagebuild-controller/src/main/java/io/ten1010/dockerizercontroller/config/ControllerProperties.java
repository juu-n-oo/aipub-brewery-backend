package io.ten1010.dockerizercontroller.config;

import io.ten1010.dockerizercontroller.cr.ImageBuildConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dockerizer.imagebuild")
@Getter
@Setter
public class ControllerProperties {

    private String group = ImageBuildConstants.GROUP;
    private String version = ImageBuildConstants.VERSION;
    private String plural = ImageBuildConstants.PLURAL;
    private String kanikoImage = ImageBuildConstants.KANIKO_DEFAULT_IMAGE;
    // 빌드 Job(과 Pod) 이 완료 후 GC 되기까지의 시간(초). 이 시간이 지나면 Pod 로그가
    // 소실되어 OpenSearch fallback 으로만 조회 가능하다.
    // CTL-10: primitive 로 두어 YAML 빈 값 → null → 언박싱 NPE 표면을 제거한다.
    private int jobTtlSeconds = 3600;
    // 빌드 Job 의 activeDeadlineSeconds 기본값(초). CR spec.buildTimeoutSeconds 미지정 시 적용.
    // 정상 빌드는 닿지 않을 만큼 넉넉히 둔 상한(wall-clock) — 멈춘 빌드가 노드를 무한 점유하는 것을 막는
    // backstop 이지, "느린 빌드 vs 멈춘 빌드" 를 구분하는 장치는 아니다(activeDeadlineSeconds 의 한계).
    private int buildTimeoutSeconds = 3600;
    // informer 재동기화(resync) 주기(초). 이 주기마다 캐시된 전 CR 이 재조정되어,
    // watch 이벤트(예: Job 완료 MODIFIED)가 유실돼도 빌드가 phase 에 영구 정지하지 않는다.
    private int resyncPeriodSeconds = 45;
    // workqueue 워커 스레드 수. 워크큐가 동일 키의 동시 처리를 막으므로 1 이면 완전 직렬.
    private int workerCount = 1;
    // C-7: Kaniko push 시 insecure registry 허용 / TLS 검증 skip 토글.
    // 내부 Harbor(self-signed) 대상이라 기본 true(현행 유지). 신뢰 가능한 레지스트리면 false 로.
    private boolean registryInsecure = true;
    private boolean registrySkipTlsVerify = true;

}
