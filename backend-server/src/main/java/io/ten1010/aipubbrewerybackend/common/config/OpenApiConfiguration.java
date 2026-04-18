package io.ten1010.aipubbrewerybackend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AIPub Brewery Backend API")
                        .version("v1")
                        .description("AIPub 플랫폼의 웹 기반 Dockerfile 편집 및 이미지 빌드/관리 서비스 API"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development server")));
    }

}
