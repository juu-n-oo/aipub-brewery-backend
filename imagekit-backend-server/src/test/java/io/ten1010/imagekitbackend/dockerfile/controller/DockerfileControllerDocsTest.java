package io.ten1010.imagekitbackend.dockerfile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.imagekitbackend.aipub.config.AipubProperties;
import io.ten1010.imagekitbackend.aipub.filter.AipubAuthenticationFilter;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.imagekitbackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.imagekitbackend.dockerfile.service.DockerfileRevisionService;
import io.ten1010.imagekitbackend.dockerfile.service.DockerfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DockerfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(RestDocumentationExtension.class)
class DockerfileControllerDocsTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AipubAuthenticationFilter aipubAuthenticationFilter;

    @MockitoBean
    private DockerfileService service;

    @MockitoBean
    private DockerfileRevisionService revisionService;

    @MockitoBean
    private AipubProperties aipubProperties;

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDocs) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocs))
                .build();
    }

    private DockerfileResponse sampleResponse() {
        return DockerfileResponse.builder()
                .id(1L)
                .project("pjw")
                .username("joonwoo")
                .name("pytorch-cuda12")
                .description("PyTorch 2.1 + CUDA 12.1 기반 학습 환경")
                .content("FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nCOPY requirements.txt /app/\nRUN pip install -r /app/requirements.txt")
                .baseImage("pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime")
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
    }

    private DockerfileResponse sampleResponseWithoutFiles() {
        return DockerfileResponse.builder()
                .id(2L)
                .project("pjw")
                .username("joonwoo")
                .name("simple-pytorch")
                .description(null)
                .content("FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nRUN pip install transformers")
                .baseImage("pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime")
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
    }

    @Test
    void createDockerfile() throws Exception {
        given(service.create(any(), any())).willReturn(sampleResponseWithoutFiles());

        DockerfileCreateRequest request = new DockerfileCreateRequest();
        request.setProject("pjw");
        request.setName("simple-pytorch");
        request.setDescription(null);
        request.setContent("FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nRUN pip install transformers");
        request.setBaseImage("pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime");

        mockMvc.perform(post("/api/v1alpha1/dockerfiles")
                        .principal(new UsernamePasswordAuthenticationToken("joonwoo", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andDo(document("dockerfile-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("project").description("프로젝트 이름 (namespace)"),
                                fieldWithPath("name").description("Dockerfile 이름"),
                                fieldWithPath("description").description("설명 (선택)").optional(),
                                fieldWithPath("content").description("Dockerfile 내용"),
                                fieldWithPath("baseImage").description("Base 이미지 (FROM 대상)")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Dockerfile ID"),
                                fieldWithPath("project").description("프로젝트 이름"),
                                fieldWithPath("username").description("소유자 사용자 이름"),
                                fieldWithPath("name").description("Dockerfile 이름"),
                                fieldWithPath("description").description("설명").optional(),
                                fieldWithPath("content").description("Dockerfile 내용"),
                                fieldWithPath("baseImage").description("Base 이미지 (FROM 대상)"),
                                fieldWithPath("createdAt").description("생성 시각"),
                                fieldWithPath("updatedAt").description("수정 시각"),
                                fieldWithPath("latestVersion").description("최신 리비전 버전 번호").optional(),
                                fieldWithPath("latestRevisionId").description("최신 리비전 ID").optional()
                        )));
    }

    @Test
    void getDockerfileById() throws Exception {
        given(service.getById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1alpha1/dockerfiles/{id}", 1L))
                .andExpect(status().isOk())
                .andDo(document("dockerfile-get",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("id").description("Dockerfile ID")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Dockerfile ID"),
                                fieldWithPath("project").description("프로젝트 이름"),
                                fieldWithPath("username").description("소유자 사용자 이름"),
                                fieldWithPath("name").description("Dockerfile 이름"),
                                fieldWithPath("description").description("설명").optional(),
                                fieldWithPath("content").description("Dockerfile 내용"),
                                fieldWithPath("baseImage").description("Base 이미지 (FROM 대상)"),
                                fieldWithPath("createdAt").description("생성 시각"),
                                fieldWithPath("updatedAt").description("수정 시각"),
                                fieldWithPath("latestVersion").description("최신 리비전 버전 번호").optional(),
                                fieldWithPath("latestRevisionId").description("최신 리비전 ID").optional()
                        )));
    }

    @Test
    void listDockerfiles() throws Exception {
        given(service.listForUser(List.of("pjw"), "joonwoo"))
                .willReturn(List.of(sampleResponse(), sampleResponseWithoutFiles()));

        mockMvc.perform(get("/api/v1alpha1/dockerfiles")
                        .param("projects", "pjw")
                        .principal(new UsernamePasswordAuthenticationToken("joonwoo", null)))
                .andExpect(status().isOk())
                .andDo(document("dockerfile-list",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("projects").description("바인딩된 프로젝트 목록(멤버 조회). 호출자 본인 소유로 자동 제한된다.").optional(),
                                parameterWithName("username").description("username 필터(관리자 전용)").optional(),
                                parameterWithName("all").description("전체 조회 여부(관리자 전용). true 면 모든 Dockerfile 을 최신순으로 조회").optional()
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("Dockerfile ID"),
                                fieldWithPath("[].project").description("프로젝트 이름"),
                                fieldWithPath("[].username").description("소유자 사용자 이름"),
                                fieldWithPath("[].name").description("Dockerfile 이름"),
                                fieldWithPath("[].description").description("설명").optional(),
                                fieldWithPath("[].content").description("Dockerfile 내용"),
                                fieldWithPath("[].baseImage").description("Base 이미지 (FROM 대상)"),
                                fieldWithPath("[].createdAt").description("생성 시각"),
                                fieldWithPath("[].updatedAt").description("수정 시각"),
                                fieldWithPath("[].latestVersion").description("최신 리비전 버전 번호").optional(),
                                fieldWithPath("[].latestRevisionId").description("최신 리비전 ID").optional()
                        )));
    }

    @Test
    void updateDockerfile() throws Exception {
        given(service.update(eq(1L), any(), any())).willReturn(sampleResponse());

        DockerfileUpdateRequest request = new DockerfileUpdateRequest();
        request.setName("pytorch-cuda12-v2");
        request.setDescription("PyTorch 2.1 + CUDA 12.1 기반 학습 환경 (업데이트)");
        request.setContent("FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nCOPY requirements.txt /app/\nRUN pip install -r /app/requirements.txt");
        request.setBaseImage("pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime");

        mockMvc.perform(put("/api/v1alpha1/dockerfiles/{id}", 1L)
                        .principal(new UsernamePasswordAuthenticationToken("joonwoo", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("dockerfile-update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("id").description("Dockerfile ID")
                        ),
                        requestFields(
                                fieldWithPath("name").description("Dockerfile 이름 (선택, 미입력 시 변경 안 함)").optional(),
                                fieldWithPath("description").description("설명 (선택)").optional(),
                                fieldWithPath("content").description("수정할 Dockerfile 내용"),
                                fieldWithPath("baseImage").description("Base 이미지 (FROM 대상)"),
                                fieldWithPath("message").description("리비전 메시지 (선택)").optional()
                        ),
                        responseFields(
                                fieldWithPath("id").description("Dockerfile ID"),
                                fieldWithPath("project").description("프로젝트 이름"),
                                fieldWithPath("username").description("소유자 사용자 이름"),
                                fieldWithPath("name").description("Dockerfile 이름"),
                                fieldWithPath("description").description("설명").optional(),
                                fieldWithPath("content").description("Dockerfile 내용"),
                                fieldWithPath("baseImage").description("Base 이미지 (FROM 대상)"),
                                fieldWithPath("createdAt").description("생성 시각"),
                                fieldWithPath("updatedAt").description("수정 시각"),
                                fieldWithPath("latestVersion").description("최신 리비전 버전 번호").optional(),
                                fieldWithPath("latestRevisionId").description("최신 리비전 ID").optional()
                        )));
    }

    @Test
    void deleteDockerfile() throws Exception {
        mockMvc.perform(delete("/api/v1alpha1/dockerfiles/{id}", 1L))
                .andExpect(status().isNoContent())
                .andDo(document("dockerfile-delete",
                        pathParameters(
                                parameterWithName("id").description("Dockerfile ID")
                        )));
    }

}
