package io.ten1010.imagekitbackend.imagebuild.service;

import io.ten1010.imagekitbackend.common.config.OpenSearchProperties;
import io.ten1010.imagekitbackend.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.Pit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 빌드 Pod 가 GC 된 뒤 OpenSearch 에 적재된 Kaniko stdout 로그를 조회하는 fallback 클라이언트.
 * {@code imagekit.opensearch.enabled=true} 일 때만 Bean 으로 등록된다.
 *
 * <p>★ 백엔드↔프론트 계약: 조회 결과가 0건이면 {@link ResourceNotFoundException} 을 던져
 * 404 로 전파한다. 빈 문자열 200 을 반환하지 않는다. 404 = "어느 소스에서도 로그 없음" 의 유일한 신호.
 *
 * <p>로그는 PIT(Point In Time) + {@code search_after} 로 페이지네이션하여 단발 {@code _search} 의
 * {@code max_result_window}(기본 10,000) 상한을 넘겨 전체를 조회한다. 단 {@code maxLines} 를 전체 줄
 * 수의 절대 상한으로 두어 메모리/응답 폭주를 막는다(초과 시 끝에 truncated 표기).
 */
@Component
@ConditionalOnProperty(prefix = "imagekit.opensearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OpenSearchBuildLogClient {

    private static final String KANIKO_CONTAINER_NAME = "kaniko";
    /** PIT 단위 _search 의 페이지 크기. OpenSearch max_result_window(기본 10,000) 를 넘지 않는다. */
    private static final int MAX_PAGE_SIZE = 10_000;
    /** PIT 유지 시간. 페이징 루프 동안만 살아있으면 되므로 짧게 둔다. */
    private static final String PIT_KEEP_ALIVE = "1m";

    private final OpenSearchClient openSearchClient;
    private final OpenSearchProperties properties;

    /**
     * (namespace, crName) 으로 OpenSearch 에서 Kaniko 빌드 로그를 조회한다.
     * Pod 이름은 {@code {crName}-job-{random}} 이므로 match_phrase_prefix 로 도출한다.
     *
     * @return 로그 본문(라인 \n join, ANSI 코드 포함). maxLines 초과분은 잘리고 truncated 표기가 붙는다.
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

        int maxLines = properties.getMaxLines();
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, maxLines));

        String pitId = createPit();
        try {
            List<String> lines = new ArrayList<>();
            List<FieldValue> searchAfter = null;
            boolean firstPage = true;
            boolean capped = false;
            long total = 0;

            while (true) {
                final List<FieldValue> after = searchAfter;
                SearchResponse<LogDoc> response = search(query, pitId, pageSize, after);

                List<Hit<LogDoc>> hits = response.hits().hits();
                if (firstPage) {
                    total = response.hits().total() != null ? response.hits().total().value() : hits.size();
                    if (hits.isEmpty()) {
                        throw new ResourceNotFoundException(
                                "Build logs not found in OpenSearch for: " + namespace + "/" + crName);
                    }
                    firstPage = false;
                }
                if (hits.isEmpty()) {
                    break;
                }

                for (Hit<LogDoc> hit : hits) {
                    if (lines.size() >= maxLines) {
                        capped = true;
                        break;
                    }
                    LogDoc doc = hit.source();
                    if (doc != null && doc.log() != null) {
                        lines.add(doc.log());
                    }
                }

                if (capped || hits.size() < pageSize) {
                    break;
                }
                searchAfter = hits.get(hits.size() - 1).sort();
            }

            String body = String.join("\n", lines);
            if (capped || total > maxLines) {
                body = body + "\n... (truncated, OpenSearch)";
            }

            log.debug("Fetched {} OpenSearch log lines for {}/{} (total={})", lines.size(), namespace, crName, total);
            return body;
        } finally {
            deletePit(pitId);
        }
    }

    private SearchResponse<LogDoc> search(Query query, String pitId, int pageSize, List<FieldValue> searchAfter) {
        SearchRequest request = SearchRequest.of(s -> {
            s.query(query)
                    .size(pageSize)
                    .pit(Pit.of(p -> p.id(pitId).keepAlive(PIT_KEEP_ALIVE)))
                    // search_after 에는 유일한 전체 순서가 필요하다. @timestamp 만으로는 동일 시각에
                    // 찍힌 줄들의 순서가 비결정적이므로 PIT 전용 타이브레이커 _shard_doc 을 추가한다.
                    .sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Asc)))
                    .sort(so -> so.field(f -> f.field("_shard_doc").order(SortOrder.Asc)));
            if (searchAfter != null) {
                s.searchAfter(searchAfter);
            }
            return s;
        });
        try {
            return openSearchClient.search(request, LogDoc.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to query OpenSearch for build logs: " + e.getMessage(), e);
        }
    }

    private String createPit() {
        try {
            return openSearchClient.createPit(c -> c
                    .index(properties.getIndexPattern())
                    .keepAlive(k -> k.time(PIT_KEEP_ALIVE))
            ).pitId();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create OpenSearch PIT: " + e.getMessage(), e);
        }
    }

    private void deletePit(String pitId) {
        if (pitId == null) {
            return;
        }
        try {
            openSearchClient.deletePit(d -> d.pitId(List.of(pitId)));
        } catch (Exception e) {
            // PIT 는 keepAlive 만료 시 자동 정리되므로 삭제 실패는 치명적이지 않다.
            log.warn("Failed to delete OpenSearch PIT {}: {}", pitId, e.getMessage());
        }
    }

    /** OpenSearch 로그 도큐먼트의 필요한 필드만 매핑. */
    public record LogDoc(String log) {
    }

}
