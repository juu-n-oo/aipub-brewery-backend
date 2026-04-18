package io.ten1010.aipubbrewerybackend.common.config;

import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileCreateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileResponse;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.DockerfileUpdateRequest;
import io.ten1010.aipubbrewerybackend.dockerfile.service.DockerfileService;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildRequest;
import io.ten1010.aipubbrewerybackend.imagebuild.dto.ImageBuildResponse;
import io.ten1010.aipubbrewerybackend.imagebuild.service.ImageBuildService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpServerConfiguration {

    @Bean
    public DockerfileMcpTools dockerfileMcpTools(DockerfileService service) {
        return new DockerfileMcpTools(service);
    }

    @Bean
    public ImageBuildMcpTools imageBuildMcpTools(ImageBuildService service) {
        return new ImageBuildMcpTools(service);
    }

    public static class DockerfileMcpTools {

        private final DockerfileService service;

        public DockerfileMcpTools(DockerfileService service) {
            this.service = service;
        }

        @Tool(description = "새 Dockerfile을 생성한다. COPY/ADD 지시자가 포함되면 거부된다.")
        public DockerfileResponse createDockerfile(
                @ToolParam(description = "프로젝트 이름 (namespace)") String project,
                @ToolParam(description = "소유자 사용자 이름") String username,
                @ToolParam(description = "Dockerfile 이름") String name,
                @ToolParam(description = "Dockerfile 내용") String content) {
            DockerfileCreateRequest request = new DockerfileCreateRequest();
            request.setProject(project);
            request.setUsername(username);
            request.setName(name);
            request.setContent(content);
            return service.create(request);
        }

        @Tool(description = "ID로 Dockerfile을 조회한다.")
        public DockerfileResponse getDockerfile(
                @ToolParam(description = "Dockerfile ID") Long id) {
            return service.getById(id);
        }

        @Tool(description = "프로젝트별 Dockerfile 목록을 조회한다.")
        public List<DockerfileResponse> listDockerfiles(
                @ToolParam(description = "프로젝트 이름") String project) {
            return service.listByProject(project);
        }

        @Tool(description = "Dockerfile content를 수정한다. COPY/ADD 지시자가 포함되면 거부된다.")
        public DockerfileResponse updateDockerfile(
                @ToolParam(description = "Dockerfile ID") Long id,
                @ToolParam(description = "수정할 Dockerfile 내용") String content) {
            DockerfileUpdateRequest request = new DockerfileUpdateRequest();
            request.setContent(content);
            return service.update(id, request);
        }

        @Tool(description = "Dockerfile을 삭제한다.")
        public String deleteDockerfile(
                @ToolParam(description = "Dockerfile ID") Long id) {
            service.delete(id);
            return "Dockerfile " + id + " deleted successfully";
        }

    }

    public static class ImageBuildMcpTools {

        private final ImageBuildService service;

        public ImageBuildMcpTools(ImageBuildService service) {
            this.service = service;
        }

        @Tool(description = "저장된 Dockerfile로 이미지 빌드를 트리거한다. Kaniko 기반으로 빌드 후 Harbor에 push한다.")
        public ImageBuildResponse triggerImageBuild(
                @ToolParam(description = "빌드할 Dockerfile의 ID") Long dockerfileId,
                @ToolParam(description = "대상 이미지 이름 (registry/project/image)") String targetImage,
                @ToolParam(description = "이미지 태그") String tag) {
            ImageBuildRequest request = new ImageBuildRequest();
            request.setDockerfileId(dockerfileId);
            request.setTargetImage(targetImage);
            request.setTag(tag);
            return service.triggerBuild(request);
        }

        @Tool(description = "이미지 빌드의 현재 상태(phase, message, imageDigest)를 조회한다.")
        public ImageBuildResponse getBuildStatus(
                @ToolParam(description = "빌드 namespace (= project)") String namespace,
                @ToolParam(description = "ImageBuild CR 이름") String name) {
            return service.getBuildStatus(namespace, name);
        }

        @Tool(description = "Kaniko 빌드 Pod의 로그를 조회한다.")
        public String getBuildLogs(
                @ToolParam(description = "빌드 namespace (= project)") String namespace,
                @ToolParam(description = "ImageBuild CR 이름") String name) {
            return service.getBuildLogs(namespace, name);
        }

    }

}
