package io.ten1010.aipubbrewerybackend.registry.service;

import io.ten1010.aipubbrewerybackend.registry.dto.ImageSearchResponse;
import io.ten1010.aipubbrewerybackend.registry.dto.ImageTagsResponse;
import io.ten1010.aipubbrewerybackend.registry.dto.RegistryImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
public class NgcRegistryService {

    private final RestClient catalogClient;
    private final RestClient registryClient;
    private final RegistryProperties.NgcConfig config;

    public NgcRegistryService(RegistryProperties.NgcConfig config, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.catalogClient = restClientBuilder.clone()
                .baseUrl(config.getCatalogUrl())
                .build();
        this.registryClient = restClientBuilder.clone()
                .baseUrl(config.getRegistryUrl())
                .build();
    }

    @SuppressWarnings("unchecked")
    public ImageSearchResponse searchImages(String query, int page, int pageSize) {
        Map<String, Object> result = catalogClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/catalog/resources")
                        .queryParam("type", "CONTAINER")
                        .queryParam("q", query != null ? query : "")
                        .queryParam("page", page)
                        .queryParam("pageSize", pageSize)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<Map<String, Object>> resources = (List<Map<String, Object>>) result.getOrDefault("resources", List.of());
        int totalCount = ((Number) result.getOrDefault("resultTotalCount", 0)).intValue();

        List<RegistryImage> images = resources.stream()
                .map(r -> RegistryImage.builder()
                        .name((String) r.get("name"))
                        .fullPath("nvcr.io/" + r.get("orgName") + "/" + r.get("name"))
                        .description((String) r.get("description"))
                        .source("NGC")
                        .build())
                .toList();

        return ImageSearchResponse.builder()
                .images(images)
                .totalCount(totalCount)
                .build();
    }

    @SuppressWarnings("unchecked")
    public ImageTagsResponse listTags(String org, String repository) {
        RestClient.RequestHeadersSpec<?> request = registryClient.get()
                .uri("/v2/{org}/{repo}/tags/list", org, repository);

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            request = request.header(HttpHeaders.AUTHORIZATION,
                    "Basic " + encodeCredentials("$oauthtoken", config.getApiKey()));
        }

        Map<String, Object> result = request
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<String> tags = (List<String>) result.getOrDefault("tags", List.of());

        return ImageTagsResponse.builder()
                .image("nvcr.io/" + org + "/" + repository)
                .tags(tags)
                .build();
    }

    private String encodeCredentials(String username, String password) {
        return java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

}
