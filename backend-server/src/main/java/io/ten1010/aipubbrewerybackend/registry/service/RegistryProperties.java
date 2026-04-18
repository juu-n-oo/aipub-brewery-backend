package io.ten1010.aipubbrewerybackend.registry.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "brewery.registry")
@Getter
@Setter
public class RegistryProperties {

    private NgcConfig ngc = new NgcConfig();
    private HuggingfaceConfig huggingface = new HuggingfaceConfig();

    @Getter
    @Setter
    public static class NgcConfig {
        private boolean enabled = true;
        private String catalogUrl = "https://api.ngc.nvidia.com/v2";
        private String registryUrl = "https://nvcr.io";
        private String apiKey;
    }

    @Getter
    @Setter
    public static class HuggingfaceConfig {
        private boolean enabled = true;
        private String dockerHubUrl = "https://hub.docker.com/v2";
        private String namespace = "huggingface";
    }

}
