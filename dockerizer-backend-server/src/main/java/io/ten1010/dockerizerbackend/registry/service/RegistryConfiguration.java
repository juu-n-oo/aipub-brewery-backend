package io.ten1010.dockerizerbackend.registry.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RegistryConfiguration {

    @Bean
    @ConditionalOnProperty(name = "dockerizer.registry.ngc.enabled", havingValue = "true", matchIfMissing = true)
    public NgcRegistryService ngcRegistryService(RegistryProperties properties) {
        return new NgcRegistryService(properties.getNgc(), RestClient.builder());
    }

    @Bean
    @ConditionalOnProperty(name = "dockerizer.registry.huggingface.enabled", havingValue = "true", matchIfMissing = true)
    public HuggingfaceRegistryService huggingfaceRegistryService(RegistryProperties properties) {
        return new HuggingfaceRegistryService(properties.getHuggingface(), RestClient.builder());
    }

}
