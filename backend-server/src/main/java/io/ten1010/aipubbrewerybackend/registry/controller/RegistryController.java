package io.ten1010.aipubbrewerybackend.registry.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.aipubbrewerybackend.registry.dto.ImageSearchResponse;
import io.ten1010.aipubbrewerybackend.registry.dto.ImageTagsResponse;
import io.ten1010.aipubbrewerybackend.registry.service.HuggingfaceRegistryService;
import io.ten1010.aipubbrewerybackend.registry.service.NgcRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/registries")
@Tag(name = "Registry", description = "외부 컨테이너 레지스트리 프록시 (NGC, HuggingFace)")
public class RegistryController {

    private final NgcRegistryService ngcService;
    private final HuggingfaceRegistryService huggingfaceService;

    @Autowired
    public RegistryController(
            @Autowired(required = false) NgcRegistryService ngcService,
            @Autowired(required = false) HuggingfaceRegistryService huggingfaceService) {
        this.ngcService = ngcService;
        this.huggingfaceService = huggingfaceService;
    }

    // --- NGC ---

    @GetMapping("/ngc/images")
    @Operation(summary = "NGC 이미지 검색", description = "NVIDIA NGC 카탈로그에서 컨테이너 이미지를 검색한다.")
    public ImageSearchResponse searchNgcImages(
            @Parameter(description = "검색어", example = "pytorch") @RequestParam(required = false) String query,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "25") int pageSize) {
        checkEnabled(ngcService, "NGC");
        return ngcService.searchImages(query, page, pageSize);
    }

    @GetMapping("/ngc/images/{org}/{repository}/tags")
    @Operation(summary = "NGC 이미지 태그 조회", description = "NGC 레지스트리에서 특정 이미지의 태그 목록을 조회한다.")
    public ImageTagsResponse listNgcTags(
            @Parameter(description = "NGC 조직", example = "nvidia") @PathVariable String org,
            @Parameter(description = "이미지 이름", example = "pytorch") @PathVariable String repository) {
        checkEnabled(ngcService, "NGC");
        return ngcService.listTags(org, repository);
    }

    // --- HuggingFace ---

    @GetMapping("/huggingface/images")
    @Operation(summary = "HuggingFace 이미지 검색", description = "HuggingFace Docker Hub 네임스페이스에서 컨테이너 이미지를 검색한다.")
    public ImageSearchResponse searchHuggingfaceImages(
            @Parameter(description = "검색어", example = "transformers") @RequestParam(required = false) String query,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "25") int pageSize) {
        checkEnabled(huggingfaceService, "HuggingFace");
        return huggingfaceService.searchImages(query, page, pageSize);
    }

    @GetMapping("/huggingface/images/{repository}/tags")
    @Operation(summary = "HuggingFace 이미지 태그 조회", description = "HuggingFace Docker Hub에서 특정 이미지의 태그 목록을 조회한다.")
    public ImageTagsResponse listHuggingfaceTags(
            @Parameter(description = "이미지 이름", example = "transformers-pytorch-gpu") @PathVariable String repository) {
        checkEnabled(huggingfaceService, "HuggingFace");
        return huggingfaceService.listTags(repository);
    }

    private void checkEnabled(Object service, String name) {
        if (service == null) {
            throw new IllegalStateException(name + " registry is not enabled");
        }
    }

}
