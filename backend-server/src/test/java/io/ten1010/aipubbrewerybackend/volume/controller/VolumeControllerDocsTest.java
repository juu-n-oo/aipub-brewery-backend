package io.ten1010.aipubbrewerybackend.volume.controller;

import io.ten1010.aipubbrewerybackend.volume.dto.*;
import io.ten1010.aipubbrewerybackend.volume.service.VolumeBrowserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VolumeController.class)
@ExtendWith(RestDocumentationExtension.class)
class VolumeControllerDocsTest {

    private MockMvc mockMvc;

    @MockitoBean
    private VolumeBrowserService service;

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDocs) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocs))
                .build();
    }

    @Test
    void listVolumes() throws Exception {
        VolumeListResponse response = VolumeListResponse.builder()
                .items(List.of(
                        VolumeInfo.builder()
                                .name("data-storage")
                                .pvcName("data-storage-43d77785")
                                .capacity("150Gi")
                                .used("0.00Gi")
                                .ready(true)
                                .build(),
                        VolumeInfo.builder()
                                .name("model-weights")
                                .pvcName("model-weights-a1b2c3d4")
                                .capacity("500Gi")
                                .used("120.50Gi")
                                .ready(true)
                                .build()
                ))
                .build();
        given(service.listVolumes("pjw")).willReturn(response);

        mockMvc.perform(get("/api/v1/volumes/{namespace}", "pjw"))
                .andExpect(status().isOk())
                .andDo(document("volume-list",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("namespace").description("프로젝트 namespace")
                        ),
                        responseFields(
                                fieldWithPath("items").description("AIPubVolume 목록"),
                                fieldWithPath("items[].name").description("AIPubVolume CR 이름"),
                                fieldWithPath("items[].pvcName").description("resolve된 PVC 이름 (= Pod 이름)"),
                                fieldWithPath("items[].capacity").description("볼륨 용량"),
                                fieldWithPath("items[].used").description("현재 사용량"),
                                fieldWithPath("items[].ready").description("사용 준비 여부")
                        )));
    }

    @Test
    void browseRootDirectory() throws Exception {
        BrowseResponse response = BrowseResponse.builder()
                .volumeName("data-storage")
                .namespace("pjw")
                .path("/")
                .entries(List.of(
                        FileEntry.builder()
                                .name("models")
                                .type(FileEntry.FileType.DIRECTORY)
                                .size(null)
                                .modifiedAt("2026-04-15T09:30:00+00:00")
                                .build(),
                        FileEntry.builder()
                                .name("datasets")
                                .type(FileEntry.FileType.DIRECTORY)
                                .size(null)
                                .modifiedAt("2026-04-10T14:00:00+00:00")
                                .build(),
                        FileEntry.builder()
                                .name("requirements.txt")
                                .type(FileEntry.FileType.FILE)
                                .size(256L)
                                .modifiedAt("2026-04-17T12:00:00+00:00")
                                .build()
                ))
                .build();
        given(service.browse("pjw", "data-storage", "/")).willReturn(response);

        mockMvc.perform(get("/api/v1/volumes/{namespace}/{volumeName}/browse", "pjw", "data-storage")
                        .param("path", "/"))
                .andExpect(status().isOk())
                .andDo(document("volume-browse",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("namespace").description("프로젝트 namespace"),
                                parameterWithName("volumeName").description("AIPubVolume 이름")
                        ),
                        queryParameters(
                                parameterWithName("path").description("조회할 경로 (기본: /)")
                        ),
                        responseFields(
                                fieldWithPath("volumeName").description("AIPubVolume 이름"),
                                fieldWithPath("namespace").description("namespace"),
                                fieldWithPath("path").description("조회한 경로"),
                                fieldWithPath("entries").description("파일/디렉토리 목록"),
                                fieldWithPath("entries[].name").description("파일/디렉토리 이름"),
                                fieldWithPath("entries[].type").description("유형 (FILE / DIRECTORY)"),
                                fieldWithPath("entries[].size").description("파일 크기 (bytes, 디렉토리는 null)").optional(),
                                fieldWithPath("entries[].modifiedAt").description("최종 수정 시각")
                        )));
    }

    @Test
    void browseSubdirectory() throws Exception {
        BrowseResponse response = BrowseResponse.builder()
                .volumeName("data-storage")
                .namespace("pjw")
                .path("/models/checkpoints")
                .entries(List.of(
                        FileEntry.builder()
                                .name("epoch_10.pt")
                                .type(FileEntry.FileType.FILE)
                                .size(1073741824L)
                                .modifiedAt("2026-04-17T12:00:00+00:00")
                                .build(),
                        FileEntry.builder()
                                .name("epoch_20.pt")
                                .type(FileEntry.FileType.FILE)
                                .size(1073741824L)
                                .modifiedAt("2026-04-18T08:30:00+00:00")
                                .build()
                ))
                .build();
        given(service.browse("pjw", "data-storage", "/models/checkpoints")).willReturn(response);

        mockMvc.perform(get("/api/v1/volumes/{namespace}/{volumeName}/browse", "pjw", "data-storage")
                        .param("path", "/models/checkpoints"))
                .andExpect(status().isOk())
                .andDo(document("volume-browse-subdir",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("namespace").description("프로젝트 namespace"),
                                parameterWithName("volumeName").description("AIPubVolume 이름")
                        ),
                        queryParameters(
                                parameterWithName("path").description("조회할 하위 경로")
                        )));
    }

}
