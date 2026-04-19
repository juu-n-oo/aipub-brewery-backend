package io.ten1010.aipubbrewerybackend.imagebuild.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildRequest;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildResponse;
import io.ten1010.aipubbrewerybackend.imagebuild.service.ImageBuildService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageBuildController.class)
@ExtendWith(RestDocumentationExtension.class)
class ImageBuildControllerDocsTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ImageBuildService service;

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDocs) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocs))
                .build();
    }

    @Test
    void triggerBuild() throws Exception {
        ImageBuildResponse response = ImageBuildResponse.builder()
                .name("imagebuild-a1b2c3d4")
                .namespace("pjw")
                .phase("Pending")
                .targetImage("harbor.aipub.io/pjw/my-pytorch:v1.0")
                .message("ImageBuild CR created successfully")
                .dockerfileId(1L)
                .username("joonwoo")
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
        given(service.triggerBuild(any())).willReturn(response);

        ImageBuildRequest request = new ImageBuildRequest();
        request.setDockerfileId(1L);
        request.setTargetImage("harbor.aipub.io/pjw/my-pytorch");
        request.setTag("v1.0");

        mockMvc.perform(post("/api/v1/builds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andDo(document("build-trigger",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("dockerfileId").description("빌드할 Dockerfile의 ID"),
                                fieldWithPath("targetImage").description("대상 이미지 이름 (registry/project/image)"),
                                fieldWithPath("tag").description("이미지 태그"),
                                fieldWithPath("pushSecretRef").description("이미지 push용 Secret 이름 (선택)").optional(),
                                fieldWithPath("buildContextPvc").description("빌드 컨텍스트로 사용할 PVC 이름 (COPY 사용 시 필요)").optional(),
                                fieldWithPath("buildContextSubPath").description("PVC 내 빌드 컨텍스트 서브 경로").optional()
                        ),
                        responseFields(
                                fieldWithPath("name").description("ImageBuild CR 이름"),
                                fieldWithPath("namespace").description("빌드 namespace (= project)"),
                                fieldWithPath("phase").description("빌드 phase (Pending/Preparing/Building/Succeeded/Failed)"),
                                fieldWithPath("targetImage").description("대상 이미지 (tag 포함)"),
                                fieldWithPath("message").description("상태 메시지"),
                                fieldWithPath("imageDigest").description("빌드 성공 시 이미지 digest").optional(),
                                fieldWithPath("dockerfileId").description("빌드에 사용된 Dockerfile ID"),
                                fieldWithPath("username").description("빌드를 실행한 사용자"),
                                fieldWithPath("createdAt").description("빌드 요청 시각"),
                                fieldWithPath("startTime").description("빌드 시작 시각").optional(),
                                fieldWithPath("completionTime").description("빌드 완료 시각").optional()
                        )));
    }

    @Test
    void getBuildStatus() throws Exception {
        ImageBuildResponse response = ImageBuildResponse.builder()
                .name("imagebuild-a1b2c3d4")
                .namespace("pjw")
                .phase("Succeeded")
                .targetImage("harbor.aipub.io/pjw/my-pytorch:v1.0")
                .message("Build completed successfully")
                .imageDigest("sha256:abc123def456")
                .dockerfileId(1L)
                .username("joonwoo")
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .startTime(Instant.parse("2026-04-18T00:00:00Z"))
                .completionTime(Instant.parse("2026-04-18T00:05:00Z"))
                .build();
        given(service.getBuildStatus("pjw", "imagebuild-a1b2c3d4")).willReturn(response);

        mockMvc.perform(get("/api/v1/builds/{namespace}/{name}", "pjw", "imagebuild-a1b2c3d4"))
                .andExpect(status().isOk())
                .andDo(document("build-status",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("namespace").description("빌드 namespace (= project)"),
                                parameterWithName("name").description("ImageBuild CR 이름")
                        ),
                        responseFields(
                                fieldWithPath("name").description("ImageBuild CR 이름"),
                                fieldWithPath("namespace").description("빌드 namespace"),
                                fieldWithPath("phase").description("빌드 phase"),
                                fieldWithPath("targetImage").description("대상 이미지"),
                                fieldWithPath("message").description("상태 메시지"),
                                fieldWithPath("imageDigest").description("이미지 digest"),
                                fieldWithPath("dockerfileId").description("빌드에 사용된 Dockerfile ID"),
                                fieldWithPath("username").description("빌드를 실행한 사용자"),
                                fieldWithPath("createdAt").description("빌드 요청 시각"),
                                fieldWithPath("startTime").description("빌드 시작 시각"),
                                fieldWithPath("completionTime").description("빌드 완료 시각")
                        )));
    }

    @Test
    void listBuilds() throws Exception {
        List<ImageBuildResponse> responses = List.of(
                ImageBuildResponse.builder()
                        .name("imagebuild-a1b2c3d4")
                        .namespace("pjw")
                        .phase("Succeeded")
                        .targetImage("harbor.aipub.io/pjw/my-pytorch:v1.0")
                        .message("Build completed successfully")
                        .imageDigest("sha256:abc123def456")
                        .dockerfileId(1L)
                        .username("joonwoo")
                        .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                        .startTime(Instant.parse("2026-04-18T00:00:00Z"))
                        .completionTime(Instant.parse("2026-04-18T00:05:00Z"))
                        .build(),
                ImageBuildResponse.builder()
                        .name("imagebuild-e5f6g7h8")
                        .namespace("pjw")
                        .phase("Building")
                        .targetImage("harbor.aipub.io/pjw/my-tf:v2.0")
                        .message("Kaniko job created, building image")
                        .dockerfileId(2L)
                        .username("joonwoo")
                        .createdAt(Instant.parse("2026-04-18T00:10:00Z"))
                        .startTime(Instant.parse("2026-04-18T00:10:00Z"))
                        .build()
        );
        given(service.listBuilds("pjw")).willReturn(responses);

        mockMvc.perform(get("/api/v1/builds")
                        .param("project", "pjw"))
                .andExpect(status().isOk())
                .andDo(document("build-list",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("project").description("프로젝트 namespace")
                        ),
                        responseFields(
                                fieldWithPath("[].name").description("ImageBuild CR 이름"),
                                fieldWithPath("[].namespace").description("빌드 namespace (= project)"),
                                fieldWithPath("[].phase").description("빌드 phase"),
                                fieldWithPath("[].targetImage").description("대상 이미지"),
                                fieldWithPath("[].message").description("상태 메시지"),
                                fieldWithPath("[].imageDigest").description("이미지 digest").optional(),
                                fieldWithPath("[].dockerfileId").description("빌드에 사용된 Dockerfile ID"),
                                fieldWithPath("[].username").description("빌드를 실행한 사용자"),
                                fieldWithPath("[].createdAt").description("빌드 요청 시각"),
                                fieldWithPath("[].startTime").description("빌드 시작 시각").optional(),
                                fieldWithPath("[].completionTime").description("빌드 완료 시각").optional()
                        )));
    }

    @Test
    void getBuildLogs() throws Exception {
        given(service.getBuildLogs("pjw", "imagebuild-a1b2c3d4"))
                .willReturn("INFO[0000] Resolved base name pytorch/pytorch:2.1.0\nINFO[0001] Building layer...");

        mockMvc.perform(get("/api/v1/builds/{namespace}/{name}/logs", "pjw", "imagebuild-a1b2c3d4"))
                .andExpect(status().isOk())
                .andDo(document("build-logs",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("namespace").description("빌드 namespace (= project)"),
                                parameterWithName("name").description("ImageBuild CR 이름")
                        )));
    }

}
