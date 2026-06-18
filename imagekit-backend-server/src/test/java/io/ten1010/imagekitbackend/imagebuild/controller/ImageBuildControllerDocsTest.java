package io.ten1010.imagekitbackend.imagebuild.controller;

import io.ten1010.imagekitbackend.aipub.filter.AipubAuthenticationFilter;
import io.ten1010.imagekitbackend.imagebuild.service.ImageBuildService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageBuildController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(RestDocumentationExtension.class)
class ImageBuildControllerDocsTest {

    private MockMvc mockMvc;

    @MockitoBean
    private AipubAuthenticationFilter aipubAuthenticationFilter;

    @MockitoBean
    private ImageBuildService service;

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDocs) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocs))
                .build();
    }

    @Test
    void getBuildLogs() throws Exception {
        given(service.getBuildLogs("pjw", "imagebuild-a1b2c3d4"))
                .willReturn("INFO[0000] Resolved base name pytorch/pytorch:2.1.0\nINFO[0001] Building layer...");

        mockMvc.perform(get("/api/v1alpha1/builds/{namespace}/{name}/logs", "pjw", "imagebuild-a1b2c3d4"))
                .andExpect(status().isOk())
                .andDo(document("build-logs",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("namespace").description("빌드 namespace (= project)"),
                                parameterWithName("name").description("ImageBuild CR 이름")
                        )));
    }

}
