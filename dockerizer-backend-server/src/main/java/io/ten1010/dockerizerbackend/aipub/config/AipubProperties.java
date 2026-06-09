package io.ten1010.dockerizerbackend.aipub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "dockerizer.aipub")
@Getter
@Setter
public class AipubProperties {

    private String baseUrl = "http://localhost:9090";

    /**
     * Dockerfile 전체 조회(all=true)를 허용할 AIPub 관리자 role 문자열.
     * selfsubjectreviews 가 반환하는 roles 와 대조한다(멤버는 {@code aipub-member}).
     */
    private List<String> adminRoles = List.of("aipub-admin");

}
