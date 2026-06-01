package io.ten1010.dockerizerbackend.aipub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dockerizer.aipub")
@Getter
@Setter
public class AipubProperties {

    private String baseUrl = "http://localhost:9090";

}
