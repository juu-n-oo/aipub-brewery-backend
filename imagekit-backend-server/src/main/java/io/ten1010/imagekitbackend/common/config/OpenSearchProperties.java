package io.ten1010.imagekitbackend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * OpenSearch 연동 설정. 빌드 완료 후 Kaniko Pod 가 GC 되어도 OpenSearch 에 적재된
 * Kaniko stdout 로그를 fallback 으로 조회하기 위한 설정이다.
 */
@Component
@ConfigurationProperties(prefix = "imagekit.opensearch")
@Getter
@Setter
public class OpenSearchProperties {

    /** OpenSearch fallback 활성화 여부. 비활성(기본)이면 관련 Bean 이 생성되지 않는다. */
    private boolean enabled = false;

    /** OpenSearch 엔드포인트 URL (예: https://opensearch-cluster-master.aipub-monitoring:9200) */
    @Nullable
    private String url;

    @Nullable
    private String username;

    @Nullable
    private String password;

    /** 조회 대상 인덱스 패턴. */
    private String indexPattern = "kube-log-*";

    /** TLS 인증서 검증 여부. false 면 trust-all + hostname 검증 비활성화. */
    private boolean verifySsl = true;

    /** TLS 검증 시 신뢰할 CA 인증서(ca.crt) 경로. */
    @Nullable
    private String caCertPath;

    /** 단일 _search 로 가져올 최대 로그 라인 수. 초과 시 truncate 표기. */
    private int maxLines = 10000;

}
