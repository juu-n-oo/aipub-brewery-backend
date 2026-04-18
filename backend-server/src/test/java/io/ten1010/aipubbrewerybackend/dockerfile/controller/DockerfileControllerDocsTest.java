package io.ten1010.aipubbrewerybackend.dockerfile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.BuildContextFileResponse;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.service.DockerfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
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
@ExtendWith(RestDocumentationExtension.class)
class DockerfileControllerDocsTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DockerfileService service;

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
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-18T00:00:00Z"))
                .contextFiles(List.of(
                        BuildContextFileResponse.builder()
                                .id(1L)
                                .fileName("requirements.txt")
                                .targetPath("requirements.txt")
                                .fileSize(256L)
                                .uploadedAt(Instant.parse("2026-04-18T00:00:00Z"))
                                .build()
                ))
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
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-18T00:00:00Z"))
                .contextFiles(List.of())
                .build();
    }

    @Test
    void createDockerfile() throws Exception {
        given(service.create(any())).willReturn(sampleResponseWithoutFiles());

        DockerfileCreateRequest request = new DockerfileCreateRequest();
        request.setProject("pjw");
        request.setUsername("joonwoo");
        request.setName("simple-pytorch");
        request.setDescription(null);
        request.setContent("FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nRUN pip install transformers");

        mockMvc.perform(post("/api/v1/dockerfiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andDo(document("dockerfile-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("project").description("프로젝트 이름 (namespace)"),
                                fieldWithPath("username").description("소유자 사용자 이름"),
                                fieldWithPath("name").description("Dockerfile 이름"),
                                fieldWithPath("description").description("설명 (선택)").optional(),
                                fieldWithPath("content").description("Dockerfile 내용")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Dockerfile ID"),
                                fieldWithPath("project").description("프로젝트 이름"),
                                fieldWithPath("username").description("소유자 사용자 이름"),
                                fieldWithPath("name").description("Dockerfile 이름"),
                                fieldWithPath("description").description("설명").optional(),
                                fieldWithPath("content").description("Dockerfile 내용"),
                                fieldWithPath("createdAt").description("생성 시각"),
                                fieldWithPath("updatedAt").description("수정 시각"),
                                fieldWithPath("contextFiles").description("빌드 컨텍스트 파일 목록")
                        )));
    }

    @Test
    void getDockerfileById() throws Exception {
        given(service.getById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/dockerfiles/{id}", 1L))
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
                                fieldWithPath("createdAt").description("생성 시각"),
                                fieldWithPath("updatedAt").description("수정 시각"),
                                fieldWithPath("contextFiles").description("빌드 컨텍스트 파일 목록"),
                                fieldWithPath("contextFiles[].id").description("파일 ID"),
                                fieldWithPath("contextFiles[].fileName").description("원본 파일 이름"),
                                fieldWithPath("contextFiles[].targetPath").description("빌드 컨텍스트 내 경로"),
                                fieldWithPath("contextFiles[].fileSize").description("파일 크기 (bytes)"),
                                fieldWithPath("contextFiles[].uploadedAt").description("업로드 시각")
                        )));
    }

    @Test
    void listDockerfiles() throws Exception {
        given(service.listByProject("pjw")).willReturn(List.of(sampleResponse(), sampleResponseWithoutFiles()));

        mockMvc.perform(get("/api/v1/dockerfiles")
                        .param("project", "pjw"))
                .andExpect(status().isOk())
                .andDo(document("dockerfile-list",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("project").description("프로젝트 이름"),
                                parameterWithName("username").description("사용자 이름 (선택)").optional()
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("Dockerfile ID"),
                                fieldWithPath("[].project").description("프로젝트 이름"),
                                fieldWithPath("[].username").description("소유자 사용자 이름"),
                                fieldWithPath("[].name").description("Dockerfile 이름"),
                                fieldWithPath("[].description").description("설명").optional(),
                                fieldWithPath("[].content").description("Dockerfile 내용"),
                                fieldWithPath("[].createdAt").description("생성 시각"),
                                fieldWithPath("[].updatedAt").description("수정 시각"),
                                fieldWithPath("[].contextFiles").description("빌드 컨텍스트 파일 목록"),
                                fieldWithPath("[].contextFiles[].id").description("파일 ID").optional(),
                                fieldWithPath("[].contextFiles[].fileName").description("원본 파일 이름").optional(),
                                fieldWithPath("[].contextFiles[].targetPath").description("빌드 컨텍스트 내 경로").optional(),
                                fieldWithPath("[].contextFiles[].fileSize").description("파일 크기 (bytes)").optional(),
                                fieldWithPath("[].contextFiles[].uploadedAt").description("업로드 시각").optional()
                        )));
    }

    @Test
    void updateDockerfile() throws Exception {
        given(service.update(eq(1L), any())).willReturn(sampleResponse());

        DockerfileUpdateRequest request = new DockerfileUpdateRequest();
        request.setName("pytorch-cuda12-v2");
        request.setDescription("PyTorch 2.1 + CUDA 12.1 기반 학습 환경 (업데이트)");
        request.setContent("FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime\nCOPY requirements.txt /app/\nRUN pip install -r /app/requirements.txt");

        mockMvc.perform(put("/api/v1/dockerfiles/{id}", 1L)
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
                                fieldWithPath("content").description("수정할 Dockerfile 내용")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Dockerfile ID"),
                                fieldWithPath("project").description("프로젝트 이름"),
                                fieldWithPath("username").description("소유자 사용자 이름"),
                                fieldWithPath("name").description("Dockerfile 이름"),
                                fieldWithPath("description").description("설명").optional(),
                                fieldWithPath("content").description("Dockerfile 내용"),
                                fieldWithPath("createdAt").description("생성 시각"),
                                fieldWithPath("updatedAt").description("수정 시각"),
                                fieldWithPath("contextFiles").description("빌드 컨텍스트 파일 목록"),
                                fieldWithPath("contextFiles[].id").description("파일 ID"),
                                fieldWithPath("contextFiles[].fileName").description("원본 파일 이름"),
                                fieldWithPath("contextFiles[].targetPath").description("빌드 컨텍스트 내 경로"),
                                fieldWithPath("contextFiles[].fileSize").description("파일 크기 (bytes)"),
                                fieldWithPath("contextFiles[].uploadedAt").description("업로드 시각")
                        )));
    }

    @Test
    void deleteDockerfile() throws Exception {
        mockMvc.perform(delete("/api/v1/dockerfiles/{id}", 1L))
                .andExpect(status().isNoContent())
                .andDo(document("dockerfile-delete",
                        pathParameters(
                                parameterWithName("id").description("Dockerfile ID")
                        )));
    }

}
