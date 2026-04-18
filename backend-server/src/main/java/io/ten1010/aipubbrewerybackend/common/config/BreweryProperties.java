package io.ten1010.aipubbrewerybackend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "brewery")
@Getter
@Setter
public class BreweryProperties {

    private DockerfileConfig dockerfile = new DockerfileConfig();

    @Getter
    @Setter
    public static class DockerfileConfig {
        private List<String> forbiddenInstructions = List.of("COPY", "ADD");
    }

}
