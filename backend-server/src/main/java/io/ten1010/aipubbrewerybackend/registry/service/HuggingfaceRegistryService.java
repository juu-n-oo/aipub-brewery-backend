package io.ten1010.aipubbrewerybackend.registry.service;

import io.ten1010.aipubbrewerybackend.registry.dto.ImageSearchResponse;
import io.ten1010.aipubbrewerybackend.registry.dto.ImageTagsResponse;
import io.ten1010.aipubbrewerybackend.registry.dto.RegistryImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
public class HuggingfaceRegistryService {

    private final RestClient dockerHubClient;
    private final RegistryProperties.HuggingfaceConfig config;

    public HuggingfaceRegistryService(RegistryProperties.HuggingfaceConfig config, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.dockerHubClient = restClientBuilder.clone()
                .baseUrl(config.getDockerHubUrl())
                .build();
    }

    @SuppressWarnings("unchecked")
    public ImageSearchResponse searchImages(String query, int page, int pageSize) {
        Map<String, Object> result = dockerHubClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repositories/{namespace}/")
                        .queryParam("page", page + 1) // Docker Hub pages are 1-based
                        .queryParam("page_size", pageSize)
                        .build(config.getNamespace()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        int totalCount = ((Number) result.getOrDefault("count", 0)).intValue();
        List<Map<String, Object>> repos = (List<Map<String, Object>>) result.getOrDefault("results", List.of());

        List<RegistryImage> images = repos.stream()
                .filter(r -> query == null || query.isBlank() ||
                        ((String) r.getOrDefault("name", "")).toLowerCase().contains(query.toLowerCase()))
                .map(r -> RegistryImage.builder()
                        .name((String) r.get("name"))
                        .fullPath(config.getNamespace() + "/" + r.get("name"))
                        .description((String) r.get("description"))
                        .source("HUGGINGFACE")
                        .build())
                .toList();

        return ImageSearchResponse.builder()
                .images(images)
                .totalCount(totalCount)
                .build();
    }

    @SuppressWarnings("unchecked")
    public ImageTagsResponse listTags(String repository) {
        Map<String, Object> result = dockerHubClient.get()
                .uri("/repositories/{namespace}/{repo}/tags/?page_size=100&ordering=-last_updated",
                        config.getNamespace(), repository)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<Map<String, Object>> tagResults = (List<Map<String, Object>>) result.getOrDefault("results", List.of());
        List<String> tags = tagResults.stream()
                .map(t -> (String) t.get("name"))
                .toList();

        return ImageTagsResponse.builder()
                .image(config.getNamespace() + "/" + repository)
                .tags(tags)
                .build();
    }

}
