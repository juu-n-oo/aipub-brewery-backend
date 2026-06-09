package io.ten1010.dockerizerbackend.imagebuild.service;

import io.ten1010.dockerizerbackend.common.config.OpenSearchProperties;
import io.ten1010.dockerizerbackend.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 빌드 Pod 가 GC 된 뒤 OpenSearch 에 적재된 Kaniko stdout 로그를 조회하는 fallback 클라이언트.
 * {@code dockerizer.opensearch.enabled=true} 일 때만 Bean 으로 등록된다.
 *
 * <p>★ 백엔드↔프론트 계약: 조회 결과가 0건이면 {@link ResourceNotFoundException} 을 던져
 * 404 로 전파한다. 빈 문자열 200 을 반환하지 않는다. 404 = "어느 소스에서도 로그 없음" 의 유일한 신호.
 */
@Component
@ConditionalOnProperty(prefix = "dockerizer.opensearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OpenSearchBuildLogClient {

    private static final String KANIKO_CONTAINER_NAME = "kaniko";

    private final OpenSearchClient openSearchClient;
    private final OpenSearchProperties properties;

    /**
     * (namespace, crName) 으로 OpenSearch 에서 Kaniko 빌드 로그를 조회한다.
     * Pod 이름은 {@code {crName}-job-{random}} 이므로 match_phrase_prefix 로 도출한다.
     *
     * @return 로그 본문(라인 \n join, ANSI 코드 포함)
     * @throws ResourceNotFoundException 매칭되는 로그가 0건일 때
     */
    public String fetchPodLogs(String namespace, String crName) {
        Query query = Query.of(q -> q
                .bool(b -> b
                        .filter(f -> f.match(m -> m
                                .field("kubernetes.namespace_name")
                                .query(fv -> fv.stringValue(namespace))))
                        .filter(f -> f.matchPhrasePrefix(m -> m
                                .field("kubernetes.pod_name")
                                .query(crName + "-job")))
                        .filter(f -> f.match(m -> m
                                .field("kubernetes.container_name")
                                .query(fv -> fv.stringValue(KANIKO_CONTAINER_NAME))))));

        SearchRequest request = SearchRequest.of(s -> s
                .index(properties.getIndexPattern())
                .query(query)
                .size(properties.getMaxLines())
                .sort(sort -> sort.field(f -> f.field("@timestamp").order(SortOrder.Asc))));

        SearchResponse<LogDoc> response;
        try {
            response = openSearchClient.search(request, LogDoc.class);
        } catch (IOException e) {
            log.error("Failed to query OpenSearch for build logs: {}/{}", namespace, crName, e);
            throw new RuntimeException("Failed to query OpenSearch for build logs: " + e.getMessage(), e);
        }

        List<Hit<LogDoc>> hits = response.hits().hits();
        if (hits.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Build logs not found in OpenSearch for: " + namespace + "/" + crName);
        }

        String body = hits.stream()
                .map(Hit::source)
                .filter(doc -> doc != null && doc.log() != null)
                .map(LogDoc::log)
                .collect(Collectors.joining("\n"));

        long total = response.hits().total() != null ? response.hits().total().value() : hits.size();
        if (total > properties.getMaxLines()) {
            // TODO PIT + search_after 로 전체 페이지네이션 (현재는 maxLines 까지만 조회)
            body = body + "\n... (truncated, OpenSearch)";
        }

        log.debug("Fetched {} OpenSearch log lines for {}/{} (total={})", hits.size(), namespace, crName, total);
        return body;
    }

    /** OpenSearch 로그 도큐먼트의 필요한 필드만 매핑. */
    public record LogDoc(String log) {
    }

}
